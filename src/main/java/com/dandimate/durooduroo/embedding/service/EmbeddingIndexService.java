package com.dandimate.durooduroo.embedding.service;
import com.dandimate.durooduroo.ai.embedding.*;
import com.dandimate.durooduroo.embedding.domain.MemoryEmbedding;
import com.dandimate.durooduroo.embedding.repository.MemoryEmbeddingRepository;
import com.dandimate.durooduroo.memory.domain.*;
import com.dandimate.durooduroo.memory.repository.MemoryEntryRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable per-entry queue; network work is outside DB transactions. Three attempts, crash lease recovery. */
@Service
public class EmbeddingIndexService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(EmbeddingIndexService.class);
    private final MemoryEmbeddingRepository index;
    private final MemoryEntryRepository entries;
    private final OpenAiEmbeddingService ai;
    private final TransactionTemplate tx;
    private final EntityManager em;
    public EmbeddingIndexService(MemoryEmbeddingRepository index,MemoryEntryRepository entries,OpenAiEmbeddingService ai,
                                  TransactionTemplate tx,EntityManager em) {
        this.index=index;this.entries=entries;this.ai=ai;this.tx=tx;this.em=em;
    }
    @Transactional public void enqueue(MemoryEntry entry) {
        if(entry.getKind()!=EntryKind.ADDON && entry.getKind()!=EntryKind.FIRST)return;
        var row=index.locked(entry.getId()).orElse(null);
        if(row==null)index.save(new MemoryEmbedding(entry,ai.model()));
        else if(!row.matches(entry,ai.model()))row.reset(entry,ai.model());
    }
    @Scheduled(initialDelayString="${app.embedding.initial-delay-ms:60000}",fixedDelayString="${app.embedding.poll-ms:10000}")
    public void poll() {
        if(!ai.configured())return;
        backfill();
        for(int i=0;i<10 && runNext();i++) { /* bounded batch */ }
    }
    public void backfill() {
        tx.executeWithoutResult(s->{
            var old=em.createQuery("select e from MemoryEntry e where (e.kind = com.dandimate.durooduroo.memory.domain.EntryKind.ADDON or e.kind = com.dandimate.durooduroo.memory.domain.EntryKind.FIRST) and not exists (select v.entryId from MemoryEmbedding v where v.entryId=e.id)",MemoryEntry.class)
                .setMaxResults(50).getResultList();
            old.forEach(this::enqueue);
        });
    }
    private record Work(String id,String hash,long version,String text) {}
    public boolean runNext() {
        Work work=tx.execute(s->{
            var rows=index.claimable(LocalDateTime.now(),PageRequest.of(0,1));
            if(rows.isEmpty())return null;
            var row=rows.getFirst();var entry=entries.findById(row.getEntryId()).orElse(null);
            if(entry==null){index.delete(row);return null;}
            if(!row.matches(entry,ai.model()))row.reset(entry,ai.model());
            row.claim();index.flush();
            return new Work(row.getEntryId(),row.getSourceHash(),row.getVersion(),entry.getTranscript());
        });
        if(work==null)return false;
        try {
            var result=ai.embed(EmbeddingText.chunks(work.text()));
            tx.executeWithoutResult(s->index.locked(work.id()).ifPresent(row->{
                var entry=entries.findById(work.id()).orElse(null);
                if(entry!=null && row.getVersion()==work.version() && row.matches(entry,result.model()) && row.getSourceHash().equals(work.hash()))
                    row.ready(result.vectors(),result.dimensions());
            }));
        } catch(RuntimeException ex) {
            log.warn("Embedding attempt failed entryId={} failureType={}",work.id(),ex.getClass().getSimpleName());
            tx.executeWithoutResult(s->index.locked(work.id()).ifPresent(row->{
                if(row.getVersion()==work.version() && row.getSourceHash().equals(work.hash()))row.failed();
            }));
        }
        return true;
    }
}

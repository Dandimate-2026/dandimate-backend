package com.dandimate.durooduroo.chat.service;
import com.dandimate.durooduroo.ai.chat.*;
import com.dandimate.durooduroo.ai.embedding.*;
import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.common.exception.*;
import com.dandimate.durooduroo.embedding.repository.MemoryEmbeddingRepository;
import com.dandimate.durooduroo.embedding.service.EmbeddingText;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final ChatRecordAccess access;
    private final MemoryEmbeddingRepository index;
    private final OpenAiEmbeddingService embeddings;
    private final ChatAiService ai;
    private final double threshold;
    private final Semaphore capacity=new Semaphore(4);
    public ChatService(ChatRecordAccess access,MemoryEmbeddingRepository index,OpenAiEmbeddingService embeddings,
                       ChatAiService ai,@Value("${app.chat.similarity-threshold:0.25}") double threshold) {
        this.access=access;this.index=index;this.embeddings=embeddings;this.ai=ai;this.threshold=threshold;
    }
    public ChatMessageResponse answer(String userId,String question) {
        if(!capacity.tryAcquire())throw new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE,"잠시 후 다시 질문해 주세요.");
        try {return respond(userId,question.strip());}
        catch(AiFailure e) {
            throw new BusinessException(e.getCode()==AiFailure.Code.INVALID_OUTPUT?ErrorCode.AI_INVALID_OUTPUT:
                e.getCode()==AiFailure.Code.OPENAI_TIMEOUT?ErrorCode.AI_TIMEOUT:ErrorCode.AI_TEMPORARILY_UNAVAILABLE,"AI 응답을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } finally {capacity.release();}
    }
    private record Scored(ChatRecordAccess.Record record,double score) {}
    private ChatMessageResponse respond(String userId,String question) {
        var allowed=access.load(userId,false);
        if(allowed.isEmpty())return empty();
        var query=embeddings.embed(EmbeddingText.chunks(question)).vectors();
        List<Scored> scored=new ArrayList<>();
        boolean pending=false;
        var rows=new HashMap<String,com.dandimate.durooduroo.embedding.domain.MemoryEmbedding>();
        index.findAllById(allowed.keySet()).forEach(r->rows.put(r.getEntryId(),r));
        for(var r:allowed.values()) {
            var row=rows.get(r.entryId());
            if(row==null||!row.ready()||!row.getModel().equals(embeddings.model())||!row.getSourceHash().equals(r.hash())){pending=true;continue;}
            double score=-1;
            for(var q:query)for(var vector:row.vectors())score=Math.max(score,cosine(q,vector));
            if(score>=threshold)scored.add(new Scored(r,score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s->s.record().entryId()));
        if(scored.isEmpty()) {
            if(pending)throw new BusinessException(ErrorCode.AI_TEMPORARILY_UNAVAILABLE,"기록 검색을 준비하고 있습니다. 잠시 후 다시 질문해 주세요.");
            return empty();
        }
        var selected=scored.stream().limit(8).map(Scored::record).toList();
        // Snapshot again immediately before sending any original text to the external model.
        var before=access.load(userId,false);
        requireUnchanged(selected,before);
        var evidence=selected.stream().map(r->{
            var fresh=before.get(r.entryId());var source=fresh.source();
            return new ChatCommand.Evidence(r.entryId(),source.photo().photoId(),source.room().name(),
                source.photo().uploader().nickname(),fresh.entry().author().nickname(),fresh.entry().text());
        }).toList();
        var answer=ai.answer(new ChatCommand(question,evidence));
        Set<String> permitted=new HashSet<>();selected.forEach(r->permitted.add(r.entryId()));
        if(answer.answer()==null||answer.answer().isBlank()||answer.answer().length()>6000||
            !permitted.containsAll(answer.citedEntryIds()))throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,true,"Untrusted citation");
        // IDs are for source mapping only; never show internal UUIDs in natural language.
        if(answer.answer().matches("(?s).*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.*"))
            throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,true,"Internal identifier in answer");
        var after=access.load(userId,true);
        requireUnchanged(selected,after);
        if(answer.citedEntryIds().isEmpty())return empty();
        Map<String,ChatSourceView> sources=new LinkedHashMap<>();
        answer.citedEntryIds().forEach(id->{var s=after.get(id).source();sources.putIfAbsent(s.photo().photoId(),s);});
        return new ChatMessageResponse(answer.answer(),List.copyOf(sources.values()));
    }
    private void requireUnchanged(List<ChatRecordAccess.Record> original,Map<String,ChatRecordAccess.Record> current) {
        for(var r:original)if(!current.containsKey(r.entryId())||!current.get(r.entryId()).hash().equals(r.hash()))
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,"기록 또는 열람 권한이 변경되었습니다. 다시 질문해 주세요.");
    }
    public static double cosine(List<Double> a,List<Double> b) {
        if(a.size()!=b.size())return -1;
        double dot=0,aa=0,bb=0;
        for(int i=0;i<a.size();i++){dot+=a.get(i)*b.get(i);aa+=a.get(i)*a.get(i);bb+=b.get(i)*b.get(i);}
        return aa==0||bb==0?-1:dot/Math.sqrt(aa*bb);
    }
    private ChatMessageResponse empty(){return new ChatMessageResponse("열람 가능한 덧붙임에서 관련된 기억을 찾지 못했어요.",List.of());}
}

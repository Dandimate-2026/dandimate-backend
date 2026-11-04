package com.dandimate.durooduroo.embedding.domain;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.embedding.service.EmbeddingText;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.nio.ByteBuffer;
import java.util.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity @Table(name="memory_embeddings",indexes=@Index(name="idx_embedding_work",columnList="status,nextAttemptAt"))
public class MemoryEmbedding {
    @Id private String entryId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="entryId",insertable=false,updatable=false)
    @OnDelete(action=OnDeleteAction.CASCADE) private MemoryEntry source;
    @Column(nullable=false,length=64) private String sourceHash;
    @Column(nullable=false,length=80) private String model;
    @Column(nullable=false,length=16) private String status;
    private int dimensions;
    private int attempts;
    @Lob @Column(length=1000000) private byte[] vectors;
    @Column(nullable=false) private LocalDateTime nextAttemptAt;
    @Column(nullable=false) private LocalDateTime updatedAt;
    @Version private long version;
    protected MemoryEmbedding() {}
    public MemoryEmbedding(MemoryEntry entry,String model) { entryId=entry.getId(); source=entry; reset(entry,model); }
    public void reset(MemoryEntry entry,String model) {
        this.sourceHash=EmbeddingText.hash(entry.getTranscript());this.model=model;status="PENDING";
        vectors=null;dimensions=0;attempts=0;nextAttemptAt=LocalDateTime.now();updatedAt=nextAttemptAt;
    }
    public void claim() { status="PROCESSING";attempts++;nextAttemptAt=LocalDateTime.now().plusMinutes(3);updatedAt=LocalDateTime.now(); }
    public void ready(List<List<Double>> values,int dimensions) {
        ByteBuffer bytes=ByteBuffer.allocate(values.size()*dimensions*4);
        values.forEach(v->v.forEach(d->bytes.putFloat(d.floatValue())));
        vectors=bytes.array();this.dimensions=dimensions;status="READY";updatedAt=LocalDateTime.now();
    }
    public void failed() { status=attempts>=3?"FAILED":"PENDING";nextAttemptAt=LocalDateTime.now().plusSeconds(30L*attempts);updatedAt=LocalDateTime.now(); }
    public boolean matches(MemoryEntry entry,String model) {return this.model.equals(model)&&sourceHash.equals(EmbeddingText.hash(entry.getTranscript()));}
    public boolean ready() {return "READY".equals(status);}
    public String getEntryId(){return entryId;}
    public String getSourceHash(){return sourceHash;}
    public String getModel(){return model;}
    public String getStatus(){return status;}
    public long getVersion(){return version;}
    public List<List<Double>> vectors() {
        if(!ready()||dimensions<=0||vectors==null||vectors.length%(dimensions*4)!=0) return List.of();
        ByteBuffer b=ByteBuffer.wrap(vectors);List<List<Double>> result=new ArrayList<>();
        while(b.hasRemaining()){List<Double> vector=new ArrayList<>();for(int i=0;i<dimensions;i++)vector.add((double)b.getFloat());result.add(vector);}
        return result;
    }
}

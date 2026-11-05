package com.dandimate.durooduroo.ai.embedding;
import com.dandimate.durooduroo.ai.common.AiFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAiEmbeddingService implements EmbeddingAiService {
    public static final int DIMENSIONS=1536;
    private final String key, model;
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiEmbeddingService(RestClient.Builder builder,
        @Value("${openai.api-key:}") String key,
        @Value("${openai.embedding-model:text-embedding-3-small}") String model,
        @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        this.key=key; this.model=model;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        client=builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
    OpenAiEmbeddingService(RestClient client,String key,String model) {this.client=client;this.key=key;this.model=model;}
    public boolean configured() { return !key.isBlank(); }
    public String model() { return model; }
    @Override public EmbeddingResult embed(List<String> texts) {
        if (!configured()) throw new AiFailure(AiFailure.Code.NOT_CONFIGURED,false,"Embedding is not configured");
        if(texts.isEmpty() || texts.stream().anyMatch(String::isBlank)) throw new AiFailure(AiFailure.Code.INVALID_INPUT,false,"Empty embedding input");
        try {
            JsonNode body=client.post().uri("/embeddings").header("Authorization","Bearer "+key)
                .body(Map.of("model",model,"input",texts,"encoding_format","float","dimensions",DIMENSIONS))
                .retrieve().body(JsonNode.class);
            if(body==null || !body.path("model").asText().equals(model) || body.path("data").size()!=texts.size()) throw new IllegalArgumentException();
            List<List<Double>> vectors=new ArrayList<>(Collections.nCopies(texts.size(),null));
            for(JsonNode item:body.path("data")) {
                int index=item.path("index").asInt(-1);
                if(index<0 || index>=vectors.size() || vectors.get(index)!=null) throw new IllegalArgumentException();
                List<Double> vector=new ArrayList<>();
                for(JsonNode n:item.path("embedding")) {
                    if(!n.isNumber() || !Double.isFinite(n.asDouble())) throw new IllegalArgumentException();
                    vector.add(n.asDouble());
                }
                if(vector.size()!=DIMENSIONS || vector.stream().mapToDouble(v->v*v).sum()==0) throw new IllegalArgumentException();
                vectors.set(index,vector);
            }
            if(vectors.contains(null)) throw new IllegalArgumentException();
            return new EmbeddingResult(model,DIMENSIONS,vectors);
        } catch(org.springframework.web.client.RestClientException e) {
            throw new AiFailure(AiFailure.Code.OPENAI_FAILED,true,"Embedding provider unavailable");
        } catch(IllegalArgumentException e) {
            throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,true,"Invalid embedding response");
        }
    }
}

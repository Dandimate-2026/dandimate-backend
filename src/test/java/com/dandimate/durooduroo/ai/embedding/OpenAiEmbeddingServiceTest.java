package com.dandimate.durooduroo.ai.embedding;
import com.dandimate.durooduroo.ai.common.AiFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
class OpenAiEmbeddingServiceTest {
 final RestClient.Builder builder=RestClient.builder().baseUrl("https://api.openai.com/v1");
 final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
 final OpenAiEmbeddingService service=new OpenAiEmbeddingService(builder.build(),"test-key","text-embedding-3-small");
 @Test void mapsShuffledBatchByIndexAndSendsConfiguredModelAndDimensions() throws Exception {
  var a=Collections.nCopies(1536,0.5);var b=Collections.nCopies(1536,0.25);
  server.expect(requestTo("https://api.openai.com/v1/embeddings"))
   .andExpect(header("Authorization","Bearer test-key"))
   .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
   .andExpect(jsonPath("$.dimensions").value(1536))
   .andExpect(jsonPath("$.input[0]").value("첫 원문"))
   .andRespond(withSuccess(new ObjectMapper().writeValueAsString(Map.of("model","text-embedding-3-small","data",
    List.of(Map.of("index",1,"embedding",b),Map.of("index",0,"embedding",a)))),MediaType.APPLICATION_JSON));
  var result=service.embed(List.of("첫 원문","둘째 원문"));
  assertThat(result.vectors().getFirst()).isEqualTo(a);server.verify();
 }
 @Test void rejectsWrongDimensionsInsteadOfStoringCorruptVectors() throws Exception {
  server.expect(anything()).andRespond(withSuccess(new ObjectMapper().writeValueAsString(Map.of("model","text-embedding-3-small","data",List.of(Map.of("index",0,"embedding",List.of(1,2))))),MediaType.APPLICATION_JSON));
  assertThatThrownBy(()->service.embed(List.of("원문"))).isInstanceOf(AiFailure.class).hasMessageContaining("Invalid");
 }
 @Test void missingKeyDoesNotMakeNetworkCall() {
  var unconfigured=new OpenAiEmbeddingService(builder.build(),"","text-embedding-3-small");
  assertThatThrownBy(()->unconfigured.embed(List.of("원문"))).isInstanceOf(AiFailure.class);server.verify();
 }
}

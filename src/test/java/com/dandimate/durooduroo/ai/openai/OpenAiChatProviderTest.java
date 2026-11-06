package com.dandimate.durooduroo.ai.openai;
import com.dandimate.durooduroo.ai.chat.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
class OpenAiChatProviderTest {
 @Test void callsExistingResponsesModelWithOriginalsAndStrictCitationSchema() throws Exception {
  var builder=RestClient.builder().baseUrl("https://api.openai.com/v1");
  var server=MockRestServiceServer.bindTo(builder).build();
  var client=new OpenAiResponsesClient(builder.build(),new ObjectMapper(),"test-key","configured-model");
  ObjectProvider<OpenAiResponsesClient> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(client);
  var output=new ObjectMapper().writeValueAsString(Map.of("context",Map.of("topic","경주 여행"),"interpretation",Map.of("subject","민주","criterion","기억","direction","NONE","requestedFacets",List.of("OTHER")),"intent","FACT","outcome","ANSWER","statements",List.of(Map.of("kind","FACT","answersFacet","OTHER","text","민주의 기억입니다.","supports",List.of(Map.of("entryId","record_1","quote","경주에서 웃었어")),"memoryRefs",List.of()))));
  server.expect(requestTo("https://api.openai.com/v1/responses"))
   .andExpect(jsonPath("$.reasoning.effort").value("medium"))
   .andExpect(jsonPath("$.model").value("configured-model"))
   .andExpect(jsonPath("$.text.format.strict").value(true))
   .andExpect(jsonPath("$.text.format.schema.properties.interpretation.properties.request").doesNotExist())
   .andExpect(jsonPath("$.text.format.schema.properties.context.properties.target").doesNotExist())
   .andExpect(jsonPath("$.input[1].content[0].text").value(org.hamcrest.Matchers.containsString("경주에서 웃었어")))
   .andRespond(withSuccess(new ObjectMapper().writeValueAsString(Map.of("output",List.of(Map.of("content",
    List.of(Map.of("type","output_text","text",output)))))),MediaType.APPLICATION_JSON));
  var result=new OpenAiChatService(provider).answer(new ChatCommand("어떤 기억?",List.of(
   new ChatCommand.Evidence("entry","photo","경주","서연","민주","경주에서 웃었어"))));
  assertThat(result.context().topic()).isEqualTo("경주 여행");
  assertThat(result.context().target()).isEqualTo("민주");
  assertThat(result.context().criterion()).isEqualTo("기억");
  assertThat(result.statements().get(0).supports().get(0).entryId()).isEqualTo("entry");server.verify();
 }

 @Test void routerSendsOnlyCurrentQuestionAndNeverRecords() throws Exception {
  var builder=RestClient.builder().baseUrl("https://api.openai.com/v1");
  var server=MockRestServiceServer.bindTo(builder).build();var json=new ObjectMapper();
  var client=new OpenAiResponsesClient(builder.build(),json,"test-key","configured-model");
  ObjectProvider<OpenAiResponsesClient> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(client);
  String response=json.writeValueAsString(Map.of("kind","SMALL_TALK","reply","안녕하세요!"));
  server.expect(requestTo("https://api.openai.com/v1/responses"))
    .andExpect(jsonPath("$.reasoning.effort").value("low"))
    .andExpect(request->{
      var body=json.readTree(((org.springframework.mock.http.client.MockClientHttpRequest)request).getBodyAsString());
      var input=json.readTree(body.path("input").get(1).path("content").get(0).path("text").asText());
      assertThat(input.size()).isEqualTo(2);assertThat(input.path("question").asText()).isEqualTo("안녕");
      assertThat(input.has("records")).isFalse();
    }).andRespond(withSuccess(json.writeValueAsString(Map.of("output",List.of(Map.of("content",List.of(Map.of("type","output_text","text",response)))))),MediaType.APPLICATION_JSON));
  var result=new com.dandimate.durooduroo.chat.service.ChatIntentRouter(provider).route("안녕",true);
  assertThat(result.kind()).isEqualTo("SMALL_TALK");server.verify();
 }
}

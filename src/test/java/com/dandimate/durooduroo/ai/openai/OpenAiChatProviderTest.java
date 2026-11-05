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
  var output=new ObjectMapper().writeValueAsString(Map.of("intent","FACT","outcome","ANSWER","statements",List.of(Map.of("kind","FACT","answersFacet","OTHER","text","민주의 기억입니다.","supports",List.of(Map.of("entryId","record_1","quote","경주에서 웃었어")),"memoryRefs",List.of()))));
  server.expect(requestTo("https://api.openai.com/v1/responses"))
   .andExpect(jsonPath("$.reasoning.effort").value("medium"))
   .andExpect(jsonPath("$.model").value("configured-model"))
   .andExpect(jsonPath("$.text.format.strict").value(true))
   .andExpect(jsonPath("$.input[1].content[0].text").value(org.hamcrest.Matchers.containsString("경주에서 웃었어")))
   .andRespond(withSuccess(new ObjectMapper().writeValueAsString(Map.of("output",List.of(Map.of("content",
    List.of(Map.of("type","output_text","text",output)))))),MediaType.APPLICATION_JSON));
  var result=new OpenAiChatService(provider).answer(new ChatCommand("어떤 기억?",List.of(
   new ChatCommand.Evidence("entry","photo","경주","서연","민주","경주에서 웃었어"))));
  assertThat(result.statements().get(0).supports().get(0).entryId()).isEqualTo("entry");server.verify();
 }
}

package com.dandimate.durooduroo.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dandimate.durooduroo.ai.askback.AskbackCommand;
import com.dandimate.durooduroo.ai.common.AiFailure;
import com.dandimate.durooduroo.ai.common.AiInputs.EntryInput;
import com.dandimate.durooduroo.ai.common.AiInputs.MemberInput;
import com.dandimate.durooduroo.ai.common.AiInputs.PhotoInput;
import com.dandimate.durooduroo.ai.reply.ReplyClassificationCommand;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** API 키 없이 가짜 HTTP 서버로 Responses API 요청 형식(Structured Outputs)과 응답 매핑·오류 변환을 확인한다. */
class OpenAiProvidersTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiResponsesClient client = new OpenAiResponsesClient(builder.build(), JSON, "test-key", "test-model");

    @Test
    void 되묻기_요청은_json_schema_strict로_보내고_결과를_record로_바꾼다() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.additionalProperties").value(false))
                .andExpect(jsonPath("$.input[0].content[0].text").value(Matchers.containsString("되묻기")))
                .andExpect(jsonPath("$.input[1].content[0].text").value(Matchers.containsString("다들 웃은 사진")))
                .andRespond(withSuccess(output(Map.of("shouldAsk", true, "gapType", "MISSING_REASON", "confidence", 0.82, "targetMemberId", "m1",
                        "question", "서연 님, 이때 다 같이 웃은 이유가 뭐였어요?", "evidenceEntryIds", List.of("e1"), "reason", "이유가 없음")), MediaType.APPLICATION_JSON)
                        .header("x-request-id", "req_123"));

        var result = new OpenAiAskbackAiService(client).analyze(new AskbackCommand(new PhotoInput("p1", "m1", null),
                List.of(new MemberInput("m1", "서연", List.of())), List.of(new EntryInput("e1", "m1", "서연", "FIRST", "다들 웃은 사진")), List.of(),
                new AskbackCommand.Limits(List.of("m1"), List.of("MISSING_REASON"))));

        server.verify();
        assertThat(result.shouldAsk()).isTrue();
        assertThat(result.targetMemberId()).isEqualTo("m1");
        assertThat(result.evidenceEntryIds()).containsExactly("e1");
        assertThat(result.openaiRequestId()).isEqualTo("req_123");
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.promptVersion()).isEqualTo("askback-v1");
    }

    @Test
    void 엮기_결과의_문단과_인용을_읽는다() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess(output(Map.of("paragraphs", List.of(Map.of(
                "photoId", "p1", "body", "지수는 ‘다리가 풀리는 줄 알았어’라고 기억했다.",
                "citations", List.of(Map.of("entryId", "e2", "memberId", "m2", "exactQuote", "다리가 풀리는 줄 알았어")),
                "safety", Map.of("action", "ALLOW", "categories", List.of()))))), MediaType.APPLICATION_JSON));

        var result = new OpenAiScrollWeaveAiService(client).weave(new ScrollWeaveCommand(new ScrollWeaveCommand.RoomInput("r", "경주"), List.of(), Map.of()));

        assertThat(result.paragraphs()).singleElement().satisfies(p -> {
            assertThat(p.photoId()).isEqualTo("p1");
            assertThat(p.citations()).singleElement().satisfies(c -> assertThat(c.exactQuote()).isEqualTo("다리가 풀리는 줄 알았어"));
            assertThat(p.safety().action()).isEqualTo("ALLOW");
        });
    }

    @Test
    void rate_limit은_재시도_가능한_실패이고_거절은_INVALID_OUTPUT이다() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess(
                JSON.writeValueAsString(Map.of("output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "refusal", "refusal", "no"))))))
                , MediaType.APPLICATION_JSON));
        var reply = new OpenAiReplyAiService(client);
        var command = new ReplyClassificationCommand("본문", "r1", "민호", "ㅋㅋ");

        assertThatThrownBy(() -> reply.classify(command)).isInstanceOfSatisfying(AiFailure.class, e -> {
            assertThat(e.getCode()).isEqualTo(AiFailure.Code.OPENAI_RATE_LIMITED);
            assertThat(e.isRetryable()).isTrue();
        });
        assertThatThrownBy(() -> reply.classify(command)).isInstanceOfSatisfying(AiFailure.class,
                e -> assertThat(e.getCode()).isEqualTo(AiFailure.Code.INVALID_OUTPUT));
    }

    @Test
    void 키나_모델이_없으면_호출하지_않는다() {
        var unconfigured = new OpenAiResponsesClient(builder.build(), JSON, "", "test-model");
        assertThatThrownBy(() -> new OpenAiReplyAiService(unconfigured).classify(new ReplyClassificationCommand("본문", "r1", "민호", "맞아")))
                .isInstanceOfSatisfying(AiFailure.class, e -> assertThat(e.getCode()).isEqualTo(AiFailure.Code.NOT_CONFIGURED));
    }

    private static String output(Map<String, Object> structured) throws Exception {
        return JSON.writeValueAsString(Map.of("output", List.of(Map.of("type", "message",
                "content", List.of(Map.of("type", "output_text", "text", JSON.writeValueAsString(structured)))))));
    }
}

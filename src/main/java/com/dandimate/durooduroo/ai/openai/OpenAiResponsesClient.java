/**
 * OpenAI Responses API를 Structured Outputs(json_schema, strict)로 호출하는 공통 클라이언트입니다 (명세 5장 공통 클라이언트).
 * - Job 경로 전용이라 재시도는 하지 않는다(Job 재시도로 일원화). 요청당 60초 제한.
 * - 프롬프트 원문은 resources/prompts/{promptVersion}.md에서 읽고, 결과에는 버전만 남긴다.
 * - 요청·응답 원문은 로그에 남기지 않고 요청 ID·모델·지연 시간만 남긴다.
 */
package com.dandimate.durooduroo.ai.openai;

import com.dandimate.durooduroo.ai.common.AiFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);

    public record Result(JsonNode output, String model, String requestId) {}

    private final RestClient client;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    @Autowired
    public OpenAiResponsesClient(RestClient.Builder builder, ObjectMapper json, @Value("${openai.api-key:}") String apiKey,
                                 @Value("${openai.text-model:}") String model, @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        this(builder.baseUrl(baseUrl).requestFactory(timeouts()).build(), json, apiKey, model);
    }

    /** 테스트에서 가짜 HTTP 서버에 묶은 RestClient를 넣는다. */
    OpenAiResponsesClient(RestClient client, ObjectMapper json, String apiKey, String model) {
        this.client = client;
        this.json = json;
        this.apiKey = apiKey;
        this.model = model;
    }

    private static JdkClientHttpRequestFactory timeouts() {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        return requestFactory;
    }

    /** schema는 JSON Schema(object). input은 command를 JSON으로 직렬화해 사용자 메시지로 보낸다. */
    public Result call(String promptVersion, String schemaName, Map<String, Object> schema, Object input) {
        if (apiKey.isBlank() || model.isBlank()) throw new AiFailure(AiFailure.Code.NOT_CONFIGURED, false, "OPENAI_API_KEY 또는 OPENAI_TEXT_MODEL이 없습니다.");
        Map<String, Object> body;
        try {
            body = Map.of(
                    "model", model,
                    "input", List.of(
                            Map.of("role", "system", "content", List.of(Map.of("type", "input_text", "text", prompt(promptVersion)))),
                            Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", json.writeValueAsString(input))))),
                    "text", Map.of("format", Map.of("type", "json_schema", "name", schemaName, "strict", true, "schema", schema)));
        } catch (IOException e) {
            throw new AiFailure(AiFailure.Code.INVALID_INPUT, false, "AI 입력을 만들지 못했습니다.");
        }
        long started = System.nanoTime();
        try {
            var response = client.post().uri("/responses").header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(JsonNode.class);
            String requestId = response.getHeaders().getFirst("x-request-id");
            log.info("AI {} done model={} requestId={} latencyMs={}", promptVersion, model, requestId, (System.nanoTime() - started) / 1_000_000);
            return new Result(json.readTree(outputText(response.getBody())), model, requestId);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) throw new AiFailure(AiFailure.Code.OPENAI_RATE_LIMITED, true, "AI rate limited");
            throw new AiFailure(AiFailure.Code.OPENAI_FAILED, status >= 500, "AI failed with status " + status);
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof HttpTimeoutException || e.getCause() instanceof java.net.SocketTimeoutException;
            throw new AiFailure(timeout ? AiFailure.Code.OPENAI_TIMEOUT : AiFailure.Code.OPENAI_FAILED, true, "AI call failed");
        } catch (IOException e) {
            throw new AiFailure(AiFailure.Code.INVALID_OUTPUT, true, "AI output is not JSON");
        }
    }

    /** Responses API 응답에서 output[].content[] 중 output_text를 꺼낸다. 거절(refusal)은 INVALID_OUTPUT. */
    static String outputText(JsonNode response) {
        if (response == null) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT, true, "empty AI response");
        for (JsonNode item : response.path("output")) {
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) return content.path("text").asText();
                if ("refusal".equals(content.path("type").asText())) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT, false, "AI refused");
            }
        }
        throw new AiFailure(AiFailure.Code.INVALID_OUTPUT, true, "AI response has no output_text");
    }

    private String prompt(String promptVersion) throws IOException {
        return new ClassPathResource("prompts/" + promptVersion + ".md").getContentAsString(StandardCharsets.UTF_8);
    }
}

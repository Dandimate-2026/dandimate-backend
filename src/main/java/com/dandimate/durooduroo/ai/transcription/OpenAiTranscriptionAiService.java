/**
 * OpenAI Audio Transcriptions를 호출하는 받아쓰기 구현입니다 (명세 5장).
 * - 사용자가 기다리는 동기 경로이므로 요청당 15초 제한, 재시도 가능한 실패(rate limit·timeout)는 1회 재시도한다.
 * - 키가 없으면 NOT_CONFIGURED. 음성·전사문은 로그에 남기지 않고 요청 ID와 모델만 남긴다.
 */
package com.dandimate.durooduroo.ai.transcription;

import com.dandimate.durooduroo.ai.common.AiFailure;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@ConditionalOnProperty(name = "app.stt.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiTranscriptionAiService implements TranscriptionAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptionAiService.class);
    private static final String PROMPT_VERSION = "transcribe-v1";

    private final RestClient client;
    private final String apiKey;
    private final String model;

    public OpenAiTranscriptionAiService(@Value("${openai.api-key:}") String apiKey, @Value("${openai.stt-model:gpt-transcribe}") String model) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl("https://api.openai.com/v1").requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public TranscriptionResult transcribe(TranscriptionCommand command) {
        if (apiKey.isBlank()) throw new AiFailure(AiFailure.Code.NOT_CONFIGURED, false, "OPENAI_API_KEY가 설정되지 않았습니다.");
        try {
            return call(command);
        } catch (AiFailure e) {
            if (!e.isRetryable()) throw e;
            return call(command);
        }
    }

    private TranscriptionResult call(TranscriptionCommand command) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("model", model);
        body.add("language", "ko");
        if (!command.keywords().isEmpty()) body.add("prompt", "등장할 수 있는 이름: " + String.join(", ", command.keywords()));
        body.add("file", new ByteArrayResource(command.audio()) {
            @Override
            public String getFilename() {
                return command.fileName();
            }
        });
        long started = System.nanoTime();
        try {
            var response = client.post().uri("/audio/transcriptions").header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().toEntity(Map.class);
            String requestId = response.getHeaders().getFirst("x-request-id");
            log.info("STT done model={} requestId={} latencyMs={}", model, requestId, (System.nanoTime() - started) / 1_000_000);
            Object text = response.getBody() == null ? null : response.getBody().get("text");
            if (text == null || text.toString().isBlank()) throw new AiFailure(AiFailure.Code.OPENAI_FAILED, false, "음성에서 텍스트를 추출하지 못했습니다.");
            return new TranscriptionResult("1.0", text.toString().strip(), List.of("ko"), model, PROMPT_VERSION, requestId);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) throw new AiFailure(AiFailure.Code.OPENAI_RATE_LIMITED, true, "STT rate limited");
            if (status == 400 || status == 415) throw new AiFailure(AiFailure.Code.INVALID_INPUT, false, "STT rejected input");
            throw new AiFailure(AiFailure.Code.OPENAI_FAILED, status >= 500, "STT failed with status " + status);
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof HttpTimeoutException || e.getCause() instanceof java.net.SocketTimeoutException;
            throw new AiFailure(timeout ? AiFailure.Code.OPENAI_TIMEOUT : AiFailure.Code.OPENAI_FAILED, true, "STT call failed");
        }
    }
}

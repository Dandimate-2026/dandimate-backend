/** API 키 없이 개발·데모할 때 쓰는 가짜 받아쓰기. 실제 음성 내용과 무관하게 고정 문장을 돌려준다. */
package com.dandimate.durooduroo.ai.transcription;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.stt.provider", havingValue = "fake")
public class FakeTranscriptionAiService implements TranscriptionAiService {

    private final String text;

    public FakeTranscriptionAiService(@Value("${app.stt.fake-text:가짜 받아쓰기 결과예요. 내용을 고쳐서 확정해 주세요.}") String text) {
        this.text = text;
    }

    @Override
    public TranscriptionResult transcribe(TranscriptionCommand command) {
        return new TranscriptionResult("1.0", text, List.of("ko"), "fake", "transcribe-v1", null);
    }
}

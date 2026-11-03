package com.dandimate.durooduroo.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dandimate.durooduroo.ai.askback.AskbackAiService;
import com.dandimate.durooduroo.ai.reply.ParagraphReweaveAiService;
import com.dandimate.durooduroo.ai.reply.ReplyAiService;
import com.dandimate.durooduroo.ai.scene.SceneCardAiService;
import com.dandimate.durooduroo.ai.transcription.OpenAiTranscriptionAiService;
import com.dandimate.durooduroo.ai.transcription.TranscriptionAiService;
import com.dandimate.durooduroo.ai.weave.ScrollWeaveAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** app.ai.provider=openai·app.stt.provider=openai면 규칙 기반·가짜 구현 대신 OpenAI 구현만 연결된다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"app.ai.provider=openai", "app.stt.provider=openai", "app.jobs.poll-interval-ms=3600000"})
class OpenAiWiringTest {

    @Autowired private AskbackAiService askback;
    @Autowired private SceneCardAiService scene;
    @Autowired private ScrollWeaveAiService weave;
    @Autowired private ReplyAiService reply;
    @Autowired private ParagraphReweaveAiService reweave;
    @Autowired private TranscriptionAiService stt;

    @Test
    void OpenAI_구현이_연결된다() {
        assertThat(askback).isInstanceOf(OpenAiAskbackAiService.class);
        assertThat(scene).isInstanceOf(OpenAiSceneCardAiService.class);
        assertThat(weave).isInstanceOf(OpenAiScrollWeaveAiService.class);
        assertThat(reply).isInstanceOf(OpenAiReplyAiService.class);
        assertThat(reweave).isInstanceOf(OpenAiReplyAiService.class);
        assertThat(stt).isInstanceOf(OpenAiTranscriptionAiService.class);
    }
}

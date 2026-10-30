/** 음성 받아쓰기 계약 (명세 5장). 구현은 app.stt.provider로 고른다: openai(기본) 또는 fake(개발·테스트용). */
package com.dandimate.durooduroo.ai.transcription;

public interface TranscriptionAiService {
    TranscriptionResult transcribe(TranscriptionCommand command);
}

/** 받아쓰기 결과. 모든 AI 결과처럼 schemaVersion·promptVersion·model·openaiRequestId를 담는다. */
package com.dandimate.durooduroo.ai.transcription;

import java.util.List;

public record TranscriptionResult(String schemaVersion, String text, List<String> languages, String model,
                                  String promptVersion, String openaiRequestId) {
}

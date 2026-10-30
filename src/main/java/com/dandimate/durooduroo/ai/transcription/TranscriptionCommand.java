/** 받아쓰기 입력. 서버가 저장한 음성만 넘기며 임의 URL은 받지 않는다. keywords에는 방 참여자 닉네임을 넣는다. */
package com.dandimate.durooduroo.ai.transcription;

import java.util.List;

public record TranscriptionCommand(String audioAssetId, byte[] audio, String fileName, String mimeType,
                                   List<String> languages, List<String> keywords) {
}

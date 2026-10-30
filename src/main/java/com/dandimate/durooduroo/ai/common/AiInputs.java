/** AI 서비스 command가 공유하는 입력 record입니다. 도메인 서비스가 DB에서 읽어 채운다 (ai 패키지는 Repository를 쓰지 않는다). */
package com.dandimate.durooduroo.ai.common;

import java.util.List;

public final class AiInputs {

    private AiInputs() {
    }

    public record PhotoInput(String id, String uploaderMemberId, String imageUrl) {}

    public record MemberInput(String id, String nickname, List<String> aliases) {}

    /** 확정 원문. kind는 FIRST/ADDON/ASKBACK_ANSWER. */
    public record EntryInput(String id, String memberId, String nickname, String kind, String transcript) {}

    /** 욕설·놀림 처리: ALLOW / SOFTEN / OMIT. */
    public record Safety(String action, List<String> categories) {
        public static Safety allow() {
            return new Safety("ALLOW", List.of());
        }
    }

    public record Citation(String entryId, String memberId, String exactQuote) {}
}

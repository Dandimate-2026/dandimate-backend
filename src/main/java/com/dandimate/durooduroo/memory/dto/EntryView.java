/** OpenAPI Entry 스키마입니다. audioUrl은 음성 입력일 때 MediaUrls가 만든 서명 URL, 글 입력이면 null이다. */
package com.dandimate.durooduroo.memory.dto;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.dto.MemberView;
import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import java.time.LocalDateTime;

public record EntryView(String id, String photoId, MemberView member, String kind, String inputMode,
                        String transcript, String audioUrl, LocalDateTime confirmedAt) {

    public static EntryView of(MemoryEntry entry, Member author, String audioUrl) {
        return new EntryView(entry.getId(), entry.getPhotoId(), MemberView.from(author), entry.getKind().name(),
                entry.getInputMode(), entry.getTranscript(), audioUrl, entry.getConfirmedAt());
    }
}

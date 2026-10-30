/** OpenAPI ScrollResponse·Paragraph 스키마입니다. */
package com.dandimate.durooduroo.scroll.dto;

import com.dandimate.durooduroo.member.dto.MemberView;
import com.dandimate.durooduroo.photo.dto.PhotoView;
import java.time.LocalDateTime;
import java.util.List;

public record ScrollView(String id, int version, int participantCount, int memberCount, LocalDateTime generatedAt,
                         List<ParagraphView> paragraphs) {

    public record ParagraphView(String id, PhotoView photo, String body, int version, List<SourceView> sources,
                                long replyCount, int ahMatdaCount, boolean aiGenerated) {
    }

    public record SourceView(String entryId, MemberView member, String quote) {
    }
}

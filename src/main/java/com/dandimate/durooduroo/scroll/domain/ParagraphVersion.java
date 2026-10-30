/** 문단 버전 이력입니다 (명세 9장). reason: INITIAL / REPLY_ADDED / REPLY_DELETED / ENTRY_DELETED. sources는 "entryId:memberId" 목록. */
package com.dandimate.durooduroo.scroll.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "paragraph_versions", uniqueConstraints = @UniqueConstraint(name = "uk_paragraph_versions", columnNames = {"paragraphId", "version"}))
public class ParagraphVersion {
    @Id private String id;
    @Column(nullable = false) private String paragraphId;
    @Column(nullable = false) private int version;
    /** MySQL에서 @Lob String은 TINYTEXT(255바이트)가 되므로 길이를 지정해 MEDIUMTEXT로 만든다. */
    @Column(nullable = false, length = 1_000_000) private String body;
    @Column(length = 4000) private String sources;
    @Column(nullable = false, length = 20) private String reason;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected ParagraphVersion() {}

    public static ParagraphVersion of(String paragraphId, int version, String body, String sources, String reason) {
        ParagraphVersion v = new ParagraphVersion();
        v.id = UUID.randomUUID().toString(); v.paragraphId = paragraphId; v.version = version; v.body = body; v.sources = sources; v.reason = reason;
        v.createdAt = LocalDateTime.now();
        return v;
    }

    public String getParagraphId(){return paragraphId;} public int getVersion(){return version;} public String getBody(){return body;} public String getSources(){return sources;} public String getReason(){return reason;}
}

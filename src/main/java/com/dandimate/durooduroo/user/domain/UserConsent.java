package com.dandimate.durooduroo.user.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="user_consents", uniqueConstraints=@UniqueConstraint(name="uk_user_consent_user_version", columnNames={"user_id","version"}))
public class UserConsent {
    @Id private String id; @Column(name="user_id", nullable=false) private String userId; @Column(nullable=false) private String version;
    @Column(nullable=false) private boolean terms; @Column(nullable=false) private boolean mediaProcessing; @Column(nullable=false) private boolean externalAiProcessing; @Column(nullable=false) private LocalDateTime consentedAt;
    protected UserConsent() {}
    public static UserConsent of(String userId, String version, boolean terms, boolean media, boolean ai) { UserConsent c=new UserConsent(); c.id=UUID.randomUUID().toString(); c.userId=userId;c.version=version;c.terms=terms;c.mediaProcessing=media;c.externalAiProcessing=ai;c.consentedAt=LocalDateTime.now();return c; }
}

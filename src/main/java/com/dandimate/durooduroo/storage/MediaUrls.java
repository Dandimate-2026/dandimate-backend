/**
 * 사진 업로드 URL과 응답에 넣을 imageUrl을 만든다. 직접 업로드와 img 태그는 Authorization 헤더를 보낼 수 없으므로 URL 자체가 권한을 담는다.
 * - S3: presigned PUT(업로드, 10분) / presigned GET(조회)
 * - 로컬: PUT /api/v1/photos/{id}/upload, GET /api/v1/photos/{id}/image 에 expires·signature (HMAC-SHA256, 용도별로 다른 서명)
 * 서명 키(app.media.signing-secret)가 비어 있으면 시작할 때 무작위로 만들며, 그러면 재시작 후 이전 URL은 무효가 된다.
 */
package com.dandimate.durooduroo.storage;

import com.dandimate.durooduroo.memory.domain.MemoryEntry;
import com.dandimate.durooduroo.photo.domain.Photo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class MediaUrls {

    public static final Duration UPLOAD_TTL = Duration.ofMinutes(10);

    private final ObjectStorage storage;
    private final byte[] secret;
    private final Duration ttl;

    public MediaUrls(ObjectStorage storage, @Value("${app.media.signing-secret:}") String secret,
                     @Value("${app.media.url-ttl-seconds:3600}") long ttlSeconds) {
        this.storage = storage;
        this.secret = secret.isBlank() ? randomSecret() : secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String uploadUrl(Photo photo) {
        return storage.presignedPutUrl(photo.getStorageKey(), photo.getContentType(), UPLOAD_TTL)
                .orElseGet(() -> signedAppUrl("/api/v1/photos/{id}/upload", "upload", photo.getId(), UPLOAD_TTL));
    }

    /** 업로드가 끝나지 않은 사진은 null. */
    public String imageUrl(Photo photo) {
        if (!photo.isReady()) return null;
        return storage.presignedGetUrl(photo.getFileUrl(), ttl).orElseGet(() -> signedAppUrl("/api/v1/photos/{id}/image", "image", photo.getId(), ttl));
    }

    /** 음성 입력 원문의 audioUrl. 글 입력이면 null. */
    public String audioUrl(MemoryEntry entry) {
        if (entry.getAudioLocation() == null) return null;
        return storage.presignedGetUrl(entry.getAudioLocation(), ttl)
                .orElseGet(() -> signedAppUrl("/api/v1/entries/{id}/audio", "audio", entry.getId(), ttl));
    }

    public boolean verifyAudio(String entryId, long expires, String signature) {
        return verify("audio", entryId, expires, signature);
    }

    public boolean verifyUpload(String photoId, long expires, String signature) {
        return verify("upload", photoId, expires, signature);
    }

    public boolean verifyImage(String photoId, long expires, String signature) {
        return verify("image", photoId, expires, signature);
    }

    private boolean verify(String purpose, String photoId, long expires, String signature) {
        if (signature == null || Instant.now().getEpochSecond() > expires) return false;
        return MessageDigest.isEqual(sign(purpose, photoId, expires).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private String signedAppUrl(String pathTemplate, String purpose, String id, Duration validFor) {
        long expires = Instant.now().plus(validFor).getEpochSecond();
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(pathTemplate)
                .queryParam("expires", expires).queryParam("signature", sign(purpose, id, expires))
                .buildAndExpand(id).toUriString();
    }

    private String sign(String purpose, String photoId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((purpose + ":" + photoId + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}

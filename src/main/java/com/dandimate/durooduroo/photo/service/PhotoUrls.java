/**
 * 응답에 넣을 사진 imageUrl을 만든다. img 태그는 Authorization 헤더를 보낼 수 없으므로 URL 자체가 권한을 담는다.
 * - S3: presigned GET URL
 * - 로컬: /api/v1/photos/{id}/image?expires=&signature= (HMAC-SHA256 서명, 기본 1시간)
 * 서명 키(app.media.signing-secret)가 비어 있으면 시작할 때 무작위로 만들며, 그러면 재시작 후 이전 URL은 무효가 된다.
 */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.storage.ObjectStorage;
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
public class PhotoUrls {

    private final ObjectStorage storage;
    private final byte[] secret;
    private final Duration ttl;

    public PhotoUrls(ObjectStorage storage, @Value("${app.media.signing-secret:}") String secret,
                     @Value("${app.media.url-ttl-seconds:3600}") long ttlSeconds) {
        this.storage = storage;
        this.secret = secret.isBlank() ? randomSecret() : secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** 업로드가 끝나지 않은 사진은 null. */
    public String imageUrl(Photo photo) {
        if (!photo.isReady()) return null;
        return storage.presignedGetUrl(photo.getFileUrl(), ttl).orElseGet(() -> signedAppUrl(photo.getId()));
    }

    public boolean verify(String photoId, long expires, String signature) {
        if (signature == null || Instant.now().getEpochSecond() > expires) return false;
        return MessageDigest.isEqual(sign(photoId, expires).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private String signedAppUrl(String photoId) {
        long expires = Instant.now().plus(ttl).getEpochSecond();
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/photos/{photoId}/image")
                .queryParam("expires", expires).queryParam("signature", sign(photoId, expires))
                .buildAndExpand(photoId).toUriString();
    }

    private String sign(String photoId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((photoId + ":" + expires).getBytes(StandardCharsets.UTF_8));
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

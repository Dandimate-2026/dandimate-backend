/**
 * AI(장면 카드·되묻기)에 함께 보낼 사진을 읽는다. HEIC는 JPEG 파생본을 쓴다.
 * app.ai.send-photos=false이거나, JPEG·PNG가 아니거나, 읽지 못하면 null을 돌려주고 AI는 원문만으로 판단한다.
 */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.ai.common.AiInputs.ImageInput;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhotoImageLoader {

    private static final Logger log = LoggerFactory.getLogger(PhotoImageLoader.class);

    private final ObjectStorage storage;
    private final boolean enabled;

    public PhotoImageLoader(ObjectStorage storage, @Value("${app.ai.send-photos:true}") boolean enabled) {
        this.storage = storage;
        this.enabled = enabled;
    }

    public ImageInput forAi(Photo photo) {
        if (!enabled) return null;
        String contentType = photo.getDisplayLocation().equals(photo.getFileUrl()) ? photo.getContentType() : "image/jpeg";
        if (!contentType.equals("image/jpeg") && !contentType.equals("image/png")) return null;
        try {
            return storage.read(photo.getDisplayLocation()).map(bytes -> new ImageInput(contentType, bytes)).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Could not read photo {} for AI; continuing without image", photo.getId());
            return null;
        }
    }
}

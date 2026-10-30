/**
 * HEIC·HEIF 사진의 JPEG 파생본을 만든다 (명세 2장). Worker가 PHOTO_DERIVE 작업을 넘긴다.
 * 원본을 읽어 JPEG로 바꾸고 EXIF를 지운 뒤 roomId/photos/{id}-derived.jpg로 저장하고 READY로 바꾼다.
 * 실패하면 작업 재시도를 거쳐 사진을 FAILED로 두며, 원본은 지우지 않는다. 업로더가 complete를 다시 부르면 재시도한다.
 */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.job.repository.AiJobRepository;
import com.dandimate.durooduroo.notification.service.RoomEvents;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.image.HeicConverter;
import com.dandimate.durooduroo.photo.image.JpegMetadata;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PhotoDeriveService {

    private final PhotoRepository photos;
    private final AiJobRepository jobs;
    private final ObjectStorage storage;
    private final HeicConverter converter;
    private final RoomEvents events;
    private final TransactionTemplate tx;

    public PhotoDeriveService(PhotoRepository photos, AiJobRepository jobs, ObjectStorage storage, HeicConverter converter,
                              RoomEvents events, PlatformTransactionManager transactionManager) {
        this.photos = photos;
        this.jobs = jobs;
        this.storage = storage;
        this.converter = converter;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void execute(String jobId) {
        AiJob job = tx.execute(status -> jobs.findById(jobId).orElseThrow());
        Photo photo = tx.execute(status -> photos.findById(job.getTargetId()).orElse(null));
        if (photo == null || !Photo.PROCESSING.equals(photo.getStatus())) {
            finish(jobId);
            return;
        }
        byte[] original = storage.read(photo.getFileUrl()).orElseThrow(() -> new IllegalStateException("original photo missing"));
        byte[] jpeg = JpegMetadata.strip(converter.toJpeg(original));
        String key = photo.getRoomId() + "/photos/" + photo.getId() + "-derived.jpg";
        storage.putBytes(key, jpeg, "image/jpeg");
        tx.executeWithoutResult(status -> {
            Photo current = photos.findById(photo.getId()).orElseThrow();
            current.derived(storage.locationFor(key));
            jobs.findById(jobId).orElseThrow().succeed();
            events.toMember(current.getRoomId(), current.getMemberId(), "photo.uploaded",
                    Map.of("photoId", current.getId(), "roomId", current.getRoomId(), "status", current.getStatus()));
        });
    }

    /** 재시도까지 모두 실패했을 때 Worker가 부른다. */
    public void onFinalFailure(String photoId) {
        tx.executeWithoutResult(status -> photos.findById(photoId).ifPresent(photo -> {
            photo.failProcessing();
            events.toMember(photo.getRoomId(), photo.getMemberId(), "photo.uploaded",
                    Map.of("photoId", photo.getId(), "roomId", photo.getRoomId(), "status", photo.getStatus()));
        }));
    }

    private void finish(String jobId) {
        tx.executeWithoutResult(status -> jobs.findById(jobId).ifPresent(job -> {
            if ("RUNNING".equals(job.getStatus())) job.succeed();
        }));
    }
}

/**
 * 명세서 2장 사진 업로드 흐름:
 * 1. upload-requests: 서버가 저장소 key를 정해 UPLOADING 사진을 만들고 uploadUrl을 발급한다(S3 presigned PUT 또는 앱의 서명된 업로드 API).
 * 2. 클라이언트가 uploadUrl에 파일을 직접 PUT한다.
 * 3. complete: 저장소 메타데이터(HEAD)로 존재·크기(2MB)·형식을 검증하고 READY로 바꾼다. 실패하면 422 UPLOAD_VERIFICATION_FAILED.
 * 클라이언트는 저장소 key나 위치를 지정할 수 없다.
 */
package com.dandimate.durooduroo.photo.service;

import com.dandimate.durooduroo.common.auth.MemberTokenResolver;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.photo.image.JpegMetadata;
import com.dandimate.durooduroo.photo.image.PngMetadata;
import com.dandimate.durooduroo.job.domain.AiJob;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.photo.domain.Photo;
import com.dandimate.durooduroo.photo.dto.UploadRequest;
import com.dandimate.durooduroo.photo.dto.UploadResponse;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.service.RoomStateService;
import com.dandimate.durooduroo.storage.MediaUrls;
import com.dandimate.durooduroo.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoUploadService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PhotoUploadService.class);

    public static final long MAX_PHOTO_BYTES = 2_000_000;
    private final com.dandimate.durooduroo.room.repository.RoomRepository rooms;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/heic", ".heic", "image/heif", ".heif");

    private final PhotoRepository photos;
    private final MemberRepository members;
    private final RoomStateService roomStates;
    private final ObjectStorage storage;
    private final MediaUrls mediaUrls;
    private final com.dandimate.durooduroo.job.repository.AiJobRepository jobs;
    private final com.dandimate.durooduroo.notification.service.RoomEvents roomEvents;

    @org.springframework.beans.factory.annotation.Value("${app.photos.derive-wait-ms:15000}")
    private long deriveWaitMs;

    public PhotoUploadService(PhotoRepository photos, MemberRepository members, RoomStateService roomStates, ObjectStorage storage, MediaUrls mediaUrls, com.dandimate.durooduroo.notification.service.RoomEvents roomEvents,
                              com.dandimate.durooduroo.job.repository.AiJobRepository jobs, com.dandimate.durooduroo.room.repository.RoomRepository rooms) {
        this.rooms = rooms;
        this.photos = photos;
        this.members = members;
        this.roomStates = roomStates;
        this.storage = storage;
        this.mediaUrls = mediaUrls;
        this.jobs = jobs;
        this.roomEvents = roomEvents;
    }

    @Transactional
    public UploadResponse requestUploads(String roomId, String authorization, UploadRequest request) {
        var room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "방이 없습니다."));
        room.requireAllows(RoomAction.UPLOAD_OR_FIRST_ENTRY);
        Member me = members.findByRoomIdAndMemberToken(roomId, MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 업로드할 수 있습니다."));
        releaseAbandonedUploads(roomId);
        var plan = room.getPlanType();
        if (photos.countByRoomIdAndMemberId(roomId, me.getId()) + request.files().size() > plan.photosPerMember()
                || photos.countByRoomId(roomId) + request.files().size() > plan.photosPerRoom()) {
            throw new BusinessException(ErrorCode.LIMIT_REACHED, "사진 업로드 한도를 초과했습니다. 멤버당 " + plan.photosPerMember() + "장, 방당 " + plan.photosPerRoom() + "장입니다.");
        }
        if (request.files().stream().anyMatch(f -> f.displayOrder() > plan.photosPerMember()))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "사진 순서는 멤버별 사진 한도 안에서 지정해야 합니다.");
        List<UploadResponse.Upload> uploads = request.files().stream().map(file -> {
            String photoId = UUID.randomUUID().toString();
            String key = roomId + "/photos/" + photoId + EXTENSIONS.get(file.contentType());
            Photo photo = photos.save(Photo.pending(photoId, roomId, me.getId(), key, storage.locationFor(key), file.contentType(), file.displayOrder()));
            return new UploadResponse.Upload(file.clientFileId(), photoId, mediaUrls.uploadUrl(photo),
                    LocalDateTime.now().plus(MediaUrls.UPLOAD_TTL), Map.of("Content-Type", file.contentType()));
        }).toList();
        return new UploadResponse(uploads);
    }

    /** 로컬 저장소용 업로드 API. S3에서는 presigned PUT으로 저장소에 바로 올리므로 쓰이지 않는다. */
    @Transactional(readOnly = true)
    public void receiveLocalUpload(String photoId, long expires, String signature, String contentType, InputStream body) throws IOException {
        if (!mediaUrls.verifyUpload(photoId, expires, signature)) throw new BusinessException(ErrorCode.FORBIDDEN, "업로드 주소가 만료됐어요.");
        Photo photo = photos.findById(photoId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
        if (!Photo.UPLOADING.equals(photo.getStatus())) throw new BusinessException(ErrorCode.INVALID_ROOM_STATE, "이미 업로드가 끝난 사진이에요.");
        if (contentType == null || !contentType.split(";")[0].trim().equalsIgnoreCase(photo.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "업로드 요청 때 알려준 형식(" + photo.getContentType() + ")으로 올려 주세요.");
        }
        byte[] bytes = body.readNBytes((int) MAX_PHOTO_BYTES + 1);
        if (bytes.length > MAX_PHOTO_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "사진은 2MB 이하이어야 합니다.");
        storage.putBytes(photo.getStorageKey(), bytes, photo.getContentType());
    }

    /**
     * 업로드 완료 확인. 이미 READY·PROCESSING이면 그대로 돌려준다.
     * HEIC·HEIF는 PROCESSING으로 두고 PHOTO_DERIVE 작업을 만든다. 변환이 실패해 FAILED가 된 사진은 다시 부르면 재시도한다.
     */
    @Transactional
    public Photo complete(String photoId, String authorization) {
        Photo photo = photos.findById(photoId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사진이 없습니다."));
        Member me = members.findByRoomIdAndMemberToken(photo.getRoomId(), MemberTokenResolver.resolve(authorization))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참여자만 사용할 수 있습니다."));
        if (!me.getId().equals(photo.getMemberId())) throw new BusinessException(ErrorCode.FORBIDDEN, "본인 사진만 완료할 수 있습니다.");
        if (photo.isReady() || Photo.PROCESSING.equals(photo.getStatus())) return photo;
        roomStates.require(photo.getRoomId(), RoomAction.UPLOAD_OR_FIRST_ENTRY);
        if (Photo.FAILED.equals(photo.getStatus())) {
            photo.retryProcessing();
            jobs.save(AiJob.queued(photo.getRoomId(), AiJob.PHOTO_DERIVE, photo.getId()));
            return photo;
        }

        var stored = storage.head(photo.getFileUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED, "업로드된 파일을 찾을 수 없어요."));
        if (stored.size() <= 0 || stored.size() > MAX_PHOTO_BYTES) {
            throw new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED, "사진은 2MB 이하이어야 합니다.");
        }
        boolean knownType = stored.contentType() != null && stored.contentType().startsWith("image/");
        if (knownType && !stored.contentType().equalsIgnoreCase(photo.getContentType())) {
            throw new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED, "업로드 요청과 파일 형식이 달라요.");
        }
        removeMetadata(photo);
        photo.complete(hashOf(photo));
        if (Photo.PROCESSING.equals(photo.getStatus())) {
            jobs.save(AiJob.queued(photo.getRoomId(), AiJob.PHOTO_DERIVE, photo.getId()));
            return photo;
        }
        roomEvents.toMember(photo.getRoomId(), photo.getMemberId(), "photo.uploaded", Map.of("photoId", photo.getId(), "roomId", photo.getRoomId(), "status", photo.getStatus()));
        return photo;
    }

    /** 첫 한마디는 READY 사진에만 남길 수 있어서, 프론트가 바로 넘어가도 되도록 변환을 잠시 기다린다. 트랜잭션 밖에서 부른다. */
    public Photo awaitDerivative(Photo photo) {
        long deadline = System.currentTimeMillis() + deriveWaitMs;
        Photo current = photo;
        while (Photo.PROCESSING.equals(current.getStatus()) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return current;
            }
            current = photos.findById(photo.getId()).orElse(current);
        }
        return current;
    }

    /** 주소가 만료되도록 완료되지 않은 업로드는 지워서 사진 한도를 돌려준다. 파일이 없어도 삭제는 성공한다. */
    private void releaseAbandonedUploads(String roomId) {
        for (Photo photo : photos.findAllByRoomIdAndStatusAndCreatedAtBefore(roomId, Photo.UPLOADING, LocalDateTime.now().minus(MediaUrls.UPLOAD_TTL))) {
            try {
                storage.delete(photo.getFileUrl());
            } catch (RuntimeException e) {
                log.warn("Abandoned upload file not deleted: {} ({})", photo.getId(), e.getClass().getSimpleName());
            }
            photos.delete(photo);
        }
    }

    /** 브라우저가 저장소에 바로 올린 원본에서 위치 정보 같은 메타데이터를 지운다. HEIC·HEIF는 파생본을 만들 때 지운다. */
    private void removeMetadata(Photo photo) {
        if (photo.needsDerivative()) return;
        byte[] original = storage.read(photo.getFileUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED, "업로드된 파일을 찾을 수 없어요."));
        byte[] cleaned;
        try {
            cleaned = cleaned(original);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new BusinessException(ErrorCode.UPLOAD_VERIFICATION_FAILED, "사진 파일이 손상되어 올릴 수 없어요.");
        }
        if (!java.util.Arrays.equals(original, cleaned)) storage.putBytes(photo.getStorageKey(), cleaned, photo.getContentType());
    }

    private static byte[] cleaned(byte[] original) {
        if (JpegMetadata.isJpeg(original)) return JpegMetadata.stripKeepingOrientation(original);
        if (PngMetadata.isPng(original)) return PngMetadata.strip(original);
        return original;
    }

    /** 로컬 파일은 SHA-256을 계산하고, 직접 읽을 수 없는 저장소(S3)는 비워 둔다. */
    private String hashOf(Photo photo) {
        return storage.open(photo.getFileUrl()).map(resource -> {
            try (InputStream in = resource.getInputStream()) {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            } catch (Exception e) {
                return null;
            }
        }).orElse(null);
    }
}

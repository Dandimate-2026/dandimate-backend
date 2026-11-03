package com.dandimate.durooduroo.memory.service;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.memory.domain.TranscriptionDraft;
import com.dandimate.durooduroo.memory.domain.VoiceUsage;
import com.dandimate.durooduroo.memory.repository.TranscriptionDraftRepository;
import com.dandimate.durooduroo.memory.repository.VoiceUsageRepository;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.domain.RoomAction;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 외부 AI 호출 동안 DB 잠금을 보유하지 않고, 짧은 트랜잭션으로 예약/정산한다. */
@Service
public class VoiceQuotaService {
    private final RoomRepository rooms;
    private final VoiceUsageRepository usages;
    private final TranscriptionDraftRepository drafts;
    public VoiceQuotaService(RoomRepository rooms, VoiceUsageRepository usages, TranscriptionDraftRepository drafts) {
        this.rooms = rooms; this.usages = usages; this.drafts = drafts;
    }

    @Transactional
    public VoiceUsage reserve(String id, String roomId, String memberId, String hash, long durationMs, String kind) {
        Room room = lock(roomId);
        VoiceUsage existing = usages.findById(id).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 요청 키로 다른 음성을 보낼 수 없습니다.");
            if (existing.getStatus().equals("SUCCEEDED")) return existing;
            if (existing.getStatus().equals("EXPIRED"))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "중단되어 만료된 요청입니다. 새 요청 키를 사용해 주세요.");
            if (existing.getStatus().equals("RESERVED"))
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "같은 음성을 처리 중입니다.");
        }
        room.requireAllows(switch (kind) {
            case "FIRST" -> RoomAction.UPLOAD_OR_FIRST_ENTRY;
            case "ADDON" -> RoomAction.ADDON_OR_PASS;
            case "ASKBACK_ANSWER" -> RoomAction.ASKBACK_ANSWER;
            default -> RoomAction.REPLY;
        });
        long min = "FIRST".equals(kind) ? 5_000 : 1_000;
        long max = 1_000L * ("FIRST".equals(kind) ? room.getPlanType().firstSeconds() : room.getPlanType().otherSeconds());
        if (durationMs < min) throw new BusinessException(ErrorCode.MIN_DURATION_NOT_MET, "음성은 " + min / 1000 + "초 이상이어야 합니다.");
        if (durationMs > max) throw new BusinessException(ErrorCode.MAX_DURATION_EXCEEDED, "이 방에서 음성은 " + max / 1000 + "초 이하여야 합니다.");
        room.reserveVoice(durationMs);
        if (existing != null) { existing.start(); return existing; }
        return usages.save(VoiceUsage.reserve(id, roomId, memberId, hash, durationMs));
    }

    @Transactional
    public TranscriptionDraft complete(String usageId, TranscriptionDraft draft, java.util.List<String> languages) {
        Room room = lock(draft.getRoomId());
        VoiceUsage usage = usages.findById(usageId).orElseThrow();
        if (!usage.getStatus().equals("RESERVED")) throw new IllegalStateException("Voice usage is not reserved");
        drafts.save(draft);
        room.settleVoice(usage.getDurationMs(), true);
        usage.finish(draft.getId(), languages);
        return draft;
    }

    @Transactional
    public void fail(String roomId, String usageId) {
        // 방 삭제와 경합하면 방의 사용량과 내역도 이미 삭제되어 있다.
        Room room = rooms.findByIdForUpdate(roomId).orElse(null);
        if (room == null) return;
        usages.findById(usageId).filter(u -> u.getStatus().equals("RESERVED")).ifPresent(u -> {
            room.settleVoice(u.getDurationMs(), false);
            u.fail();
        });
    }

    @Transactional
    public void expire(String roomId, String usageId, java.time.LocalDateTime cutoff) {
        Room room = rooms.findByIdForUpdate(roomId).orElse(null);
        if (room == null) return;
        usages.findById(usageId).filter(u -> u.getStatus().equals("RESERVED") && u.getUpdatedAt().isBefore(cutoff)).ifPresent(u -> {
            room.settleVoice(u.getDurationMs(), false);
            u.expire();
        });
    }

    private Room lock(String roomId) {
        return rooms.findByIdForUpdate(roomId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "방이 없습니다."));
    }
}

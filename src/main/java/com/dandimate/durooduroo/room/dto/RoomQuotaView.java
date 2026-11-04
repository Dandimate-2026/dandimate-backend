package com.dandimate.durooduroo.room.dto;

import com.dandimate.durooduroo.room.domain.Room;

/** 남은 음성 시간은 처리 중 예약량을 제외한다. 사진은 업로드 대기분도 포함하되, 주소가 만료된 실패 업로드는 뺀다. */
public record RoomQuotaView(int photosPerMember, int photosPerRoom, long photoCount, long myPhotoCount,
                            long photosRemaining, long myPhotosRemaining,
                            int firstMaxSeconds, int otherMaxSeconds, long voiceLimitMs,
                            long voiceUsedMs, long voiceReservedMs, long voiceRemainingMs,
                            int regenerationLimit, int regenerationCount, int regenerationsRemaining) {
    public static RoomQuotaView from(Room room, long photoCount, long myPhotoCount) {
        var p = room.getPlanType();
        return new RoomQuotaView(p.photosPerMember(), p.photosPerRoom(), photoCount, myPhotoCount,
                Math.max(0, p.photosPerRoom() - photoCount), Math.max(0, p.photosPerMember() - myPhotoCount),
                p.firstSeconds(), p.otherSeconds(), p.voiceLimitMs(), room.getVoiceUsedMs(), room.getVoiceReservedMs(),
                room.getVoiceRemainingMs(), p.regenerations(), room.getRegenerationCount(), Math.max(0, p.regenerations() - room.getRegenerationCount()));
    }
}

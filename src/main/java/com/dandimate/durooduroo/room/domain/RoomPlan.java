package com.dandimate.durooduroo.room.domain;

/** 방 단위 이용 한도. 결제 검증 전에는 FREE만 발급한다. */
public enum RoomPlan {
    FREE(3, 30, 20, 15, 3),
    LITE(6, 60, 30, 40, 6),
    PRO(10, 120, 60, 90, 12);

    private final int photosPerMember, firstSeconds, otherSeconds, voiceMinutes, regenerations;
    RoomPlan(int photosPerMember, int firstSeconds, int otherSeconds, int voiceMinutes, int regenerations) {
        this.photosPerMember = photosPerMember; this.firstSeconds = firstSeconds;
        this.otherSeconds = otherSeconds; this.voiceMinutes = voiceMinutes; this.regenerations = regenerations;
    }
    public int photosPerMember() { return photosPerMember; }
    public int photosPerRoom() { return photosPerMember * Room.CAPACITY; }
    public int firstSeconds() { return firstSeconds; }
    public int otherSeconds() { return otherSeconds; }
    public long voiceLimitMs() { return voiceMinutes * 60_000L; }
    public int regenerations() { return regenerations; }
}

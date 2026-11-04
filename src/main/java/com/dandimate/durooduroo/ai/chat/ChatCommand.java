package com.dandimate.durooduroo.ai.chat;

import java.util.List;

/** 권한 검증을 마친 원문만 전달한다. 원문은 지시가 아닌 인용 자료로 취급한다. */
public record ChatCommand(String question, List<Evidence> evidence) {
    public ChatCommand { evidence = List.copyOf(evidence); }
    public record Evidence(String entryId, String photoId, String roomName,
                           String uploaderNickname, String authorNickname, String text) {}
}

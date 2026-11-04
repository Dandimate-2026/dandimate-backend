package com.dandimate.durooduroo.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 근거 사진과 열람 가능한 덧붙임 전체. 서버가 DB 원문으로 조립한다.
 * entries는 작성 시각 오름차순이며 동률은 entryId로 정렬한다.
 * 이미지 URL은 응답할 때 생성한 서명 URL이다. 임베딩 테이블에 저장하지 않는다.
 */
public record ChatSourceView(RoomInfo room, PhotoInfo photo, List<EntryInfo> entries) {
    public ChatSourceView { entries = List.copyOf(entries); }
    public record RoomInfo(String roomId, String name) {}
    public record PhotoInfo(String photoId, String imageUrl, LocalDateTime createdAt, ChatAuthorView uploader) {}
    public record EntryInfo(String entryId, String text, LocalDateTime createdAt, ChatAuthorView author) {}
}

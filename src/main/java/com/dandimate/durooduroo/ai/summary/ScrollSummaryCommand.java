/** 한 줄 요약 입력. 문단은 사진 순서이고, placeName은 사진에 입력한 장소(없으면 null)다. */
package com.dandimate.durooduroo.ai.summary;

import java.util.List;

public record ScrollSummaryCommand(RoomInput room, List<ParagraphInput> paragraphs) {

    public record RoomInput(String name, String meetingDate) {}

    public record ParagraphInput(String placeName, String body) {}
}

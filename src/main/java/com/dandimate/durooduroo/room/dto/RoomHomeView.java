/** OpenAPI RoomHomeResponse 스키마입니다. */
package com.dandimate.durooduroo.room.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RoomHomeView(RoomSummary room, MemberStats me, List<MemberStats> members, List<TodayFlower> todayFlowers,
                           MyTodo myTodo, long unreadCount, boolean allCompleted) {

    public record RoomSummary(String id, String name, LocalDate meetingDate, String status, LocalDateTime deadlineAt) {
    }

    public record MemberStats(String id, String nickname, String role, String flowerCode, int photoCount, int storyCount, double responseRate) {
    }

    public record TodayFlower(String memberId, String flowerCode, String entryKind) {
    }

    public record MyTodo(boolean needsFirstEntry, int pendingCirculationCount, TopAskback topAskback) {
    }

    public record TopAskback(String id, String question) {
    }
}

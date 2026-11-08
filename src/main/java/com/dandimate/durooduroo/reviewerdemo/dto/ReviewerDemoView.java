package com.dandimate.durooduroo.reviewerdemo.dto;
import java.util.List;
public record ReviewerDemoView(boolean enabled, String hostRoomId, String memberRoomId, String completedRoomId, List<DemoRoom> rooms) {
    public record DemoRoom(String roomId,String name,String status,String role,String experience) {}
    public static ReviewerDemoView off() { return new ReviewerDemoView(false,null,null,null,List.of()); }
}

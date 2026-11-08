package com.dandimate.durooduroo.reviewerdemo.dto;

/** 심사 화면이 바로 열 수 있도록 각 체험 방 ID를 돌려준다. */
public record ReviewerDemoView(String hostRoomId, String memberRoomId, String completedRoomId) { }

package com.dandimate.durooduroo.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.notification.domain.Notification;
import com.dandimate.durooduroo.notification.repository.NotificationRepository;
import com.dandimate.durooduroo.notification.service.Notifier;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoomRetentionSchedulerTest {
    @Test
    void 삭제_예정_알림에는_방_이름과_삭제일이_들어가고_중복_생성하지_않는다() {
        RoomRepository rooms = mock(RoomRepository.class);
        MemberRepository members = mock(MemberRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        Room room = Room.create("경주 여행");
        Member host = Member.host(room.getId(), "서연");
        when(members.findAllByRoomIdAndLeftAtIsNull(room.getId())).thenReturn(List.of(host));
        when(notifications.existsByMemberIdAndTypeAndResourceId(host.getId(), "ROOM_DELETION_DUE_30D", room.getId())).thenReturn(true);

        Notifier notifier = mock(Notifier.class);
        when(notifier.notifyScheduled(any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation ->
                Notification.scheduled(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5), invocation.getArgument(6)));
        new RoomRetentionScheduler(rooms, members, notifications, mock(RoomAdminService.class), notifier)
                .createWarnings(room, room.getDeleteAt().minusDays(6));

        ArgumentCaptor<String> type = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifier).notifyScheduled(eq(room.getId()), eq(host.getId()), type.capture(), eq("ROOM"), eq(room.getId()), message.capture(), eq(room.getDeleteAt()));
        assertThat(type.getValue()).isEqualTo("ROOM_DELETION_DUE_7D");
        assertThat(message.getValue()).contains("경주 여행", room.getDeleteAt().toLocalDate().toString());
        verify(notifier, never()).notifyScheduled(any(), any(), eq("ROOM_DELETION_DUE_1D"), any(), any(), any(), any());
    }
}

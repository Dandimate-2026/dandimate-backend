package com.dandimate.durooduroo.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dandimate.durooduroo.common.auth.AccountPrincipal;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.dto.SpecJoinRoomRequest;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.member.service.MemberService;
import com.dandimate.durooduroo.notification.service.*;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.room.service.RoomAdminService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.jobs.poll-interval-ms=60000")
class MemberJoinEventsTest {
    @Autowired com.dandimate.durooduroo.user.repository.UserAccountRepository users;
    @Autowired MemberService service;
    @Autowired RoomAdminService admin;
    @Autowired RoomRepository rooms;
    @Autowired MemberRepository members;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean RoomEventHub hub;
    Room room;
    String invite;
    String userId;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        room = rooms.save(Room.create("event test", LocalDate.now(), 3));
        invite = room.getIssuedInviteToken();
        userId = users.save(com.dandimate.durooduroo.user.domain.UserAccount.kakao(UUID.randomUUID().toString())).getId();
        var principal = new AccountPrincipal(userId);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void joinPublishesAfterCommitAndRepeatEntryDoesNotPublish() {
        String id = tx.execute(status -> {
            var joined = service.joinByInvite(invite, new SpecJoinRoomRequest("guest", null));
            verify(hub, never()).send(any());
            return joined.member().id();
        });
        verify(hub).send(RoomEvent.toRoom(room.getId(), "member.joined", Map.of("memberId", id)));
        clearInvocations(hub);
        service.joinByInvite(invite, new SpecJoinRoomRequest("guest", null));
        verify(hub, never()).send(any());
    }

    @Test void rejoinPublishesWithSameMemberId() {
        String id = service.joinByInvite(invite, new SpecJoinRoomRequest("guest", null)).member().id();
        tx.executeWithoutResult(status -> members.findById(id).orElseThrow().leave());
        clearInvocations(hub);
        var joined = service.joinByInvite(invite, new SpecJoinRoomRequest("again", null));
        assertThat(joined.member().id()).isEqualTo(id);
        verify(hub).send(RoomEvent.toRoom(room.getId(), "member.joined", Map.of("memberId", id)));
    }

    @Test void rolledBackJoinNeverPublishesOrPersists() {
        tx.executeWithoutResult(status -> {
            service.joinByInvite(invite, new SpecJoinRoomRequest("guest", null));
            status.setRollbackOnly();
        });
        verify(hub, never()).send(any());
        assertThat(members.findByRoomIdAndUserId(room.getId(), userId)).isEmpty();
    }

    @Test void rejectedJoinNeverPublishes() {
        Room full = rooms.save(Room.create("full", LocalDate.now(), 1));
        members.save(Member.host(full.getId(), "host"));
        assertThatThrownBy(() -> service.joinByInvite(full.getIssuedInviteToken(), new SpecJoinRoomRequest("guest", null)))
            .isInstanceOf(BusinessException.class);
        verify(hub, never()).send(any());
    }

    @Test void roomUpdatePublishesOnlyAfterSuccessfulCommit() {
        members.save(Member.host(room.getId(), "host", userId));
        tx.executeWithoutResult(status -> {
            admin.update(room.getId(), null, "updated", null, null);
            verify(hub, never()).send(any());
        });
        verify(hub).send(RoomEvent.toRoom(room.getId(), "room.updated", Map.of("roomId", room.getId())));
        clearInvocations(hub);
        assertThatThrownBy(() -> admin.update(room.getId(), null, " ", null, null)).isInstanceOf(BusinessException.class);
        tx.executeWithoutResult(status -> {
            admin.update(room.getId(), null, "rollback", null, null);
            status.setRollbackOnly();
        });
        verify(hub, never()).send(any());
        assertThat(rooms.findById(room.getId()).orElseThrow().getTitle()).isEqualTo("updated");
    }
}

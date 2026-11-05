package com.dandimate.durooduroo.member.service;

import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.member.domain.Member;
import com.dandimate.durooduroo.member.domain.MemberRole;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.notification.service.Notifier;
import com.dandimate.durooduroo.notification.service.RoomEvents;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MembershipService {
    public enum PlanRequestReason { PHOTO, VOICE, REWEAVE }

    private final MemberRepository members;
    private final com.dandimate.durooduroo.payment.PaymentOrderRepository paymentOrders;
    private final RoomRepository rooms;
    private final RoomEvents events;
    private final Notifier notifier;

    public MembershipService(MemberRepository members, RoomRepository rooms, RoomEvents events, Notifier notifier, com.dandimate.durooduroo.payment.PaymentOrderRepository paymentOrders) {
        this.paymentOrders = paymentOrders;
        this.members = members;
        this.rooms = rooms;
        this.events = events;
        this.notifier = notifier;
    }

    private Member current(String roomId) {
        rooms.findByIdForUpdate(roomId).orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "방을 찾을 수 없습니다."));
        return members.findByRoomIdAndUserId(roomId, CurrentUser.requireId())
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "방 참가자만 사용할 수 있습니다."));
    }

    private Member active(String roomId) {
        Member member = current(roomId);
        if (!member.isActive()) throw new BusinessException("FORBIDDEN", "방에서 나간 참가자입니다.");
        return member;
    }

    public Map<String, Object> rename(String roomId, String nickname) {
        Member me = active(roomId);
        String name = nickname.strip();
        if (members.existsByRoomIdAndNicknameAndIdNot(roomId, name, me.getId())) {
            throw new BusinessException("NICKNAME_TAKEN", "이미 사용 중인 이름입니다.");
        }
        me.rename(name);
        events.toRoom(roomId, "member.updated", Map.of("memberId", me.getId()));
        return Map.of("id", me.getId(), "nickname", name, "role", me.getRole(), "flowerCode", me.getFlowerCode());
    }

    public void leave(String roomId) {
        Member me = current(roomId);
        if (me.getRole() == MemberRole.HOST) throw new BusinessException("FORBIDDEN", "방장은 나가기 대신 방 삭제를 이용해 주세요.");
        if (!me.isActive()) return;
        me.leave();
        events.toRoom(roomId, "member.left", Map.of("memberId", me.getId()));
    }

    public Map<String, Object> transferHost(String roomId, String memberId) {
        Member me = active(roomId);
        if (me.getRole() != MemberRole.HOST) throw new BusinessException("FORBIDDEN", "방장만 위임할 수 있습니다.");
        Member target = members.findById(memberId)
                .filter(m -> m.getRoomId().equals(roomId) && m.isActive() && m.getRole() == MemberRole.MEMBER)
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "같은 방의 다른 활성 멤버를 선택해 주세요."));
        com.dandimate.durooduroo.payment.PaymentGuard.requireIdle(paymentOrders, roomId);
        me.changeRole(MemberRole.MEMBER);
        target.changeRole(MemberRole.HOST);
        events.toRoom(roomId, "member.updated", Map.of("memberId", target.getId(), "previousHostId", me.getId()));
        return Map.of("hostMemberId", target.getId(), "previousHostMemberId", me.getId());
    }

    public Map<String, Object> requestPlan(String roomId, PlanRequestReason reason) {
        Member me = active(roomId);
        if (me.getRole() == MemberRole.HOST) throw new BusinessException("FORBIDDEN", "방장에게 요청하는 기능입니다.");
        if (rooms.findById(roomId).orElseThrow().getPlanType() != com.dandimate.durooduroo.room.domain.RoomPlan.FREE)
            throw new BusinessException("VALIDATION_ERROR", "무료 방에서만 방장에게 플랜 변경을 요청할 수 있습니다.");
        Member host = members.findAllByRoomIdAndLeftAtIsNull(roomId).stream()
                .filter(m -> m.getRole() == MemberRole.HOST).findFirst()
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "방장을 찾을 수 없습니다."));
        // 프론트 알림 계약: type은 요청 종류, resourceType은 한도 사유, resourceId는 방 ID.
        var notification = notifier.notify(roomId, host.getId(), "PLAN_UPGRADE_REQUESTED", reason.name(), roomId);
        return Map.of("notificationId", notification.getId(), "reason", reason, "status", "REQUESTED");
    }
}

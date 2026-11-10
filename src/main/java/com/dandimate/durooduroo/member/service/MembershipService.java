package com.dandimate.durooduroo.member.service;

import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
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
        rooms.requireByIdForUpdate(roomId);
        return members.findByRoomIdAndUserId(roomId, CurrentUser.requireId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방 참가자만 사용할 수 있습니다."));
    }

    private Member active(String roomId) {
        Member member = current(roomId);
        if (!member.isActive()) throw new BusinessException(ErrorCode.FORBIDDEN, "방에서 나간 참가자입니다.");
        return member;
    }

    public Map<String, Object> rename(String roomId, String nickname) {
        Member me = active(roomId);
        String name = nickname.strip();
        if (members.existsByRoomIdAndNicknameAndIdNot(roomId, name, me.getId())) {
            throw new BusinessException(ErrorCode.NICKNAME_TAKEN, "이미 사용 중인 이름입니다.");
        }
        me.rename(name);
        events.toRoom(roomId, "member.updated", Map.of("memberId", me.getId()));
        return Map.of("id", me.getId(), "nickname", name, "role", me.getRole(), "flowerCode", me.getFlowerCode());
    }

    public void leave(String roomId) {
        Member me = current(roomId);
        if (me.getRole() == MemberRole.HOST) throw new BusinessException(ErrorCode.FORBIDDEN, "방장은 나가기 대신 방 삭제를 이용해 주세요.");
        if (!me.isActive()) return;
        me.leave();
        events.toRoom(roomId, "member.left", Map.of("memberId", me.getId()));
    }

    public void kick(String roomId, String memberId) {
        Member host = active(roomId);
        if (host.getRole() != MemberRole.HOST) throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 멤버를 내보낼 수 있습니다.");
        Member target = members.findById(memberId).filter(m -> m.getRoomId().equals(roomId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자가 없습니다."));
        if (target.getId().equals(host.getId()) || target.getRole() == MemberRole.HOST)
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장 자신은 내보낼 수 없습니다.");
        if (target.isKicked()) return;
        target.kick();
        events.toRoom(roomId, "member.left", Map.of("memberId", target.getId(), "reason", "KICKED"));
    }

    public Map<String, Object> transferHost(String roomId, String memberId) {
        Member me = active(roomId);
        if (me.getRole() != MemberRole.HOST) throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 위임할 수 있습니다.");
        Member target = members.findById(memberId)
                .filter(m -> m.getRoomId().equals(roomId) && m.isActive() && m.getRole() == MemberRole.MEMBER)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "같은 방의 다른 활성 멤버를 선택해 주세요."));
        com.dandimate.durooduroo.payment.PaymentGuard.requireIdle(paymentOrders, roomId);
        me.changeRole(MemberRole.MEMBER);
        target.changeRole(MemberRole.HOST);
        events.toRoom(roomId, "member.updated", Map.of("memberId", target.getId(), "previousHostId", me.getId()));
        String message = me.getNickname() + "\uB2D8\uC774 " + target.getNickname() + "\uB2D8\uC5D0\uAC8C \uBC29\uC7A5\uC744 \uB118\uACBC\uC5B4\uC694.";
        for (Member recipient : members.findAllByRoomIdAndLeftAtIsNull(roomId)) {
            notifier.notify(roomId, recipient.getId(), "HOST_TRANSFERRED", "ROOM", roomId, message);
        }
        return Map.of("hostMemberId", target.getId(), "previousHostMemberId", me.getId());
    }

    public Map<String, Object> requestPlan(String roomId, PlanRequestReason reason, com.dandimate.durooduroo.room.domain.RoomPlan requestedPlan) {
        Member me = active(roomId);
        if (me.getRole() == MemberRole.HOST) throw new BusinessException(ErrorCode.FORBIDDEN, "\uBC29\uC7A5\uC5D0\uAC8C \uC694\uCCAD\uD558\uB294 \uAE30\uB2A5\uC785\uB2C8\uB2E4.");
        var room = rooms.requireById(roomId);
        var currentPlan = room.getPlanType();
        if (currentPlan == com.dandimate.durooduroo.room.domain.RoomPlan.PRO)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "\uC774\uBBF8 \uAC00\uC7A5 \uB192\uC740 \uD50C\uB79C\uC785\uB2C8\uB2E4.");
        var targetPlan = requestedPlan == null
                ? (currentPlan == com.dandimate.durooduroo.room.domain.RoomPlan.FREE ? com.dandimate.durooduroo.room.domain.RoomPlan.LITE : com.dandimate.durooduroo.room.domain.RoomPlan.PRO)
                : requestedPlan;
        if (targetPlan.priceKrw() <= currentPlan.priceKrw())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "\uD604\uC7AC \uD50C\uB79C\uBCF4\uB2E4 \uB192\uC740 \uD50C\uB79C\uB9CC \uC694\uCCAD\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
        Member host = members.findAllByRoomIdAndLeftAtIsNull(roomId).stream()
                .filter(m -> m.getRole() == MemberRole.HOST).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "\uBC29\uC7A5\uC744 \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));
        String message = me.getNickname() + "\uB2D8\uC774 " + targetPlan.name() + " \uD50C\uB79C \uC5C5\uADF8\uB808\uC774\uB4DC\uB97C \uC694\uCCAD\uD588\uC5B4\uC694. (" + reason.name() + ")";
        var notification = notifier.notify(roomId, host.getId(), "PLAN_UPGRADE_REQUESTED", reason.name(), roomId, message);
        return Map.of("notificationId", notification.getId(), "reason", reason, "targetPlan", targetPlan, "status", "REQUESTED");
    }

}

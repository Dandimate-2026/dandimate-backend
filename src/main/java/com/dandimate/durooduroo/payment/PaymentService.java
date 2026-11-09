package com.dandimate.durooduroo.payment;

import com.dandimate.durooduroo.common.auth.CurrentUser;
import com.dandimate.durooduroo.common.exception.*;
import com.dandimate.durooduroo.member.domain.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.notification.service.RoomEvents;
import com.dandimate.durooduroo.room.domain.*;
import com.dandimate.durooduroo.room.dto.SpecCreateRoomRequest;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    private final PaymentOrderRepository orders;
    private final RoomRepository rooms;
    private final MemberRepository members;
    private final PaymentGateway gateway;
    private final ObjectMapper json;
    private final RoomEvents events;
    private final com.dandimate.durooduroo.notification.service.Notifier notifier;
    private final TransactionTemplate tx;
    private final com.dandimate.durooduroo.room.service.RoomDetailsService details;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    public PaymentService(PaymentOrderRepository orders, RoomRepository rooms, MemberRepository members,
            PaymentGateway gateway, ObjectMapper json, RoomEvents events, PlatformTransactionManager manager, com.dandimate.durooduroo.room.service.RoomDetailsService details, com.dandimate.durooduroo.notification.service.Notifier notifier) {
        this.details = details;
        this.orders = orders; this.rooms = rooms; this.members = members; this.gateway = gateway;
        this.json = json; this.events = events; this.tx = new TransactionTemplate(manager); this.notifier = notifier;
    }

    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.chat.quota.ChatQuotaService quota;
    public record OrderView(String orderId, String orderName, int amount, String currency, String status,
                            String roomId, RoomPlan fromPlan, RoomPlan targetPlan, boolean testMode, String orderType, String packId, int creditAmount) {}
    private OrderView view(PaymentOrder o) {
        return new OrderView(o.getId(), o.isCredit() ? "두루두루 챗봇 " + o.getCreditAmount() + "회" : "두루두루 " + o.getTargetPlan().name() + " 방 플랜", o.getAmount(), "KRW",
                o.getStatus(), o.getRoomId(), o.getFromPlan(), o.getTargetPlan(), true, o.getOrderType(), o.getPackId(), o.getCreditAmount());
    }

    public OrderView createCreditOrder(String pack) {
        String user=CurrentUser.requireId();configured();
        return tx.execute(s->view(orders.save(PaymentOrder.credit(user,pack))));
    }
    public OrderView createRoomOrder(SpecCreateRoomRequest request) {
        String user = CurrentUser.requireId(); configured();
        try { request.consent().validate(); }
        catch (IllegalArgumentException ex) { throw new BusinessException(ErrorCode.CONSENT_REQUIRED, ex.getMessage()); }
        if (!request.planType().requiresPayment()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "무료 방은 방 생성 API를 이용해 주세요.");
        details.validateCover(request.selectedCover(), user);
        // Validate domain constraints before charging anything.
        Room.create(request.name().strip(), request.meetingDate(), request.selectedCapacity(), request.planType());
        if (request.name().isBlank() || request.hostNickname().isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이름을 입력해 주세요.");
        try {
            String body = json.writeValueAsString(request);
            return tx.execute(s -> view(orders.save(PaymentOrder.create(user, null, RoomPlan.FREE, request.planType(), body))));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    public OrderView createUpgradeOrder(String roomId, RoomPlan target) {
        String user = CurrentUser.requireId(); configured();
        return tx.execute(s -> {
            Room room = hostRoom(roomId, user);
            PaymentGuard.requireIdle(orders, roomId);
            if (target.priceKrw() <= room.getPlanType().priceKrw())
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "상위 플랜으로만 변경할 수 있습니다.");
            return view(orders.save(PaymentOrder.create(user, roomId, room.getPlanType(), target, null)));
        });
    }

    public OrderView get(String id) {
        String user = CurrentUser.requireId();
        return tx.execute(s -> view(own(id, user, false)));
    }

    public OrderView confirm(String id, String key, int amount) {
        String user = CurrentUser.requireId(); configured();
        // Persist the key and room reservation before the external side effect.
        tx.executeWithoutResult(s -> {
            PaymentOrder snapshot = own(id, user, false);
            Room room = snapshot.getRoomId() == null ? null : rooms.findByIdForUpdate(snapshot.getRoomId()).orElse(null);
            PaymentOrder o = own(id, user, true);
            if (o.getAmount() != amount || (o.getPaymentKey() != null && !key.equals(o.getPaymentKey())))
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "주문 금액 또는 결제 키가 일치하지 않습니다.");
            if ("DONE".equals(o.getStatus())) return;
            if ("FAILED".equals(o.getStatus())) throw new BusinessException(ErrorCode.PAYMENT_REJECTED, "종료된 주문입니다.");
            if ("READY".equals(o.getStatus()) && o.getCreatedAt().plusMinutes(30).isBefore(LocalDateTime.now()))
                throw new BusinessException(ErrorCode.PAYMENT_REJECTED, "주문이 만료되었습니다. 새 주문을 만들어 주세요.");
            if (o.getRoomId() != null) {
                if (room == null) throw new BusinessException(ErrorCode.ROOM_NOT_FOUND, "방이 삭제되었습니다.");
                requireHost(room.getId(), user);
                if (room.getPlanType() != o.getFromPlan()) throw new BusinessException(ErrorCode.VERSION_CONFLICT, "플랜이 변경되었습니다. 새 주문을 만들어 주세요.");
                if (orders.existsByRoomIdAndStatusAndIdNot(room.getId(), "PROCESSING", id))
                    throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS, "이 방에 확인 중인 결제가 있습니다.");
            }
            o.start(key);
        });
        // Serialize retries with the same order. Provider requests are bounded (5/15 seconds).
        // Keeping the room lock here also prevents deletion/delegation while finalizing approval.
        try { return tx.execute(s -> {
            PaymentOrder snapshot = own(id, user, false);
            Room room = snapshot.getRoomId() == null ? null : rooms.findByIdForUpdate(snapshot.getRoomId()).orElse(null);
            PaymentOrder o = own(id, user, true);
            if ("DONE".equals(o.getStatus())) return view(o);
            gateway.approve(key, id, o.getAmount());
            if(o.isCredit()) {
                quota.grant(user,o.getCreditAmount());
                o.succeed(null);
                return view(o);
            }
            if (o.getRoomRequest() != null) {
                try {
                    var request = json.readValue(o.getRoomRequest(), SpecCreateRoomRequest.class);
                    room = rooms.save(Room.create(request.name().strip(), request.meetingDate(), request.selectedCapacity(), o.getTargetPlan()));
                    if (request.deadlineAt() != null)
                        room.changeDeadline(request.deadlineAt().atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime(), LocalDateTime.now());
                    details.apply(room, request.place(), request.selectedCover(), user);
                    members.save(Member.host(room.getId(), request.hostNickname().strip(), user));
                } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
            } else {
                if (room == null) throw new IllegalStateException("Reserved payment room missing");
                RoomPlan previousPlan = room.getPlanType();
                room.upgradePlan(o.getTargetPlan());
                String message = "\uBC29 \uD50C\uB79C\uC774 " + previousPlan.name() + "\uC5D0\uC11C " + room.getPlanType().name() + "(\uC73C)\uB85C \uC5C5\uADF8\uB808\uC774\uB4DC\uB410\uC5B4\uC694.";
                for (Member recipient : members.findAllByRoomIdAndLeftAtIsNull(room.getId())) {
                    notifier.notify(room.getId(), recipient.getId(), "ROOM_PLAN_UPGRADED", "ROOM", room.getId(), message);
                }
            }
            o.succeed(room.getId());
            events.toRoom(room.getId(), "room.updated", Map.of("planType", room.getPlanType().name()));
            return view(o);
        }); } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.PAYMENT_REJECTED) {
                tx.executeWithoutResult(s -> {
                    PaymentOrder snapshot = own(id, user, false);
                    if (snapshot.getRoomId() != null) rooms.findByIdForUpdate(snapshot.getRoomId());
                    PaymentOrder order = own(id, user, true);
                    if ("PROCESSING".equals(order.getStatus())) order.fail();
                });
            }
            throw ex;
        }
    }

    private PaymentOrder own(String id, String user, boolean lock) {
        PaymentOrder order = (lock ? orders.lock(id) : orders.findById(id))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "주문이 없습니다."));
        if (lock) em.refresh(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!order.getUserId().equals(user)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "주문이 없습니다.");
        return order;
    }
    private Room hostRoom(String id, String user) {
        Room room = rooms.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND, "방이 없습니다."));
        requireHost(id, user); return room;
    }
    private void requireHost(String id, String user) {
        members.findByRoomIdAndUserId(id, user).filter(m -> m.isActive() && m.getRole() == MemberRole.HOST)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "방장만 결제할 수 있습니다."));
    }
    private void configured() {
        if (!gateway.configured()) throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED, "토스 테스트 결제 키 설정이 필요합니다.");
    }
}

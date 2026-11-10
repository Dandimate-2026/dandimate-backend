package com.dandimate.durooduroo.payment;

import com.dandimate.durooduroo.common.exception.BusinessException;
import com.dandimate.durooduroo.common.exception.ErrorCode;
import com.dandimate.durooduroo.room.domain.RoomPlan;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Approval attempts retain their payment key across crashes/timeouts; never grant twice. */
@Entity
@Table(name = "payment_orders")
public class PaymentOrder {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String userId;
    @Column(length = 36) private String roomId;
    @Enumerated(EnumType.STRING) @Column(columnDefinition = "varchar(10)") private RoomPlan fromPlan;
    @Enumerated(EnumType.STRING) @Column(columnDefinition = "varchar(10)") private RoomPlan targetPlan;
    @Column(nullable = false) private int amount;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 200, unique = true) private String paymentKey;
    @Column(length = 4000) private String roomRequest;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    @Column(nullable=false,length=16) private String orderType="ROOM";
    @Column(length=16) private String packId;
    private int creditAmount;
    public String getOrderType() { return orderType; }
    public String getPackId() { return packId; }
    public int getCreditAmount() { return creditAmount; }
    public boolean isCredit() { return "CREDIT".equals(orderType); }
    public static PaymentOrder credit(String user,String pack) {
        int[] values=switch(pack) {
            case "light" -> new int[]{10,900};
            case "basic" -> new int[]{30,1900};
            case "plenty" -> new int[]{50,2900};
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR,"크레딧 상품을 확인해 주세요.");
        };
        PaymentOrder o=new PaymentOrder();o.id=UUID.randomUUID().toString();o.userId=user;
        o.orderType="CREDIT";o.packId=pack;o.creditAmount=values[0];o.amount=values[1];
        o.status="READY";o.createdAt=LocalDateTime.now();return o;
    }
    protected PaymentOrder() {}
    public static PaymentOrder create(String user, String room, RoomPlan from, RoomPlan target, String request) {
        PaymentOrder o = new PaymentOrder();
        o.id = UUID.randomUUID().toString(); o.userId = user; o.roomId = room;
        o.fromPlan = from; o.targetPlan = target; o.amount = target.priceKrw() - from.priceKrw();
        o.roomRequest = request; o.status = "READY"; o.createdAt = LocalDateTime.now();
        return o;
    }
    public void start(String key) { paymentKey = key; status = "PROCESSING"; }
    public void succeed(String room) { roomId = room; status = "DONE"; approvedAt = LocalDateTime.now(); roomRequest = null; }
    public void fail() { status = "FAILED"; }
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getRoomId() { return roomId; }
    public RoomPlan getFromPlan() { return fromPlan; }
    public RoomPlan getTargetPlan() { return targetPlan; }
    public int getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getPaymentKey() { return paymentKey; }
    public String getRoomRequest() { return roomRequest; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

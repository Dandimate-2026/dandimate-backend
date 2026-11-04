package com.dandimate.durooduroo.payment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOrder p where p.id = :id")
    Optional<PaymentOrder> lock(String id);
    boolean existsByRoomIdAndStatus(String roomId, String status);
    boolean existsByRoomIdAndStatusAndIdNot(String roomId, String status, String id);
}

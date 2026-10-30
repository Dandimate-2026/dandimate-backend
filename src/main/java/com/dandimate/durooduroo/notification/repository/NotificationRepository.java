/** 알림 저장소입니다. */
package com.dandimate.durooduroo.notification.repository;
import com.dandimate.durooduroo.notification.domain.Notification; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<Notification,String>{List<Notification> findAllByMemberIdOrderByCreatedAtDesc(String memberId); long countByMemberIdAndReadAtIsNull(String memberId);}

package com.dandimate.durooduroo.notification.repository;
import com.dandimate.durooduroo.notification.domain.PushSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, String> { Optional<PushSubscription> findByEndpoint(String endpoint); java.util.List<PushSubscription> findAllByUserId(String userId); void deleteByEndpointAndUserId(String endpoint, String userId); }

package com.dandimate.durooduroo.user.repository;
import com.dandimate.durooduroo.user.domain.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserConsentRepository extends JpaRepository<UserConsent, String> { boolean existsByUserIdAndVersion(String userId, String version); }

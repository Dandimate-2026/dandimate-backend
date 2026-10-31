package com.dandimate.durooduroo.user.repository;
import com.dandimate.durooduroo.user.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}

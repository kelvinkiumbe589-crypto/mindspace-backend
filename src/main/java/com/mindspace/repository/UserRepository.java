package com.mindspace.repository;

import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByHandle(String handle);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByHandle(String handle);
    List<User> findByHandleIsNull();
    long countByCreatedAtAfter(LocalDateTime time);

    // Recipients of the daily mood reminder: ordinary members who haven't opted out.
    List<User> findByRoleAndMoodReminderEnabledTrue(User.Role role);

    // Telegram bot linking.
    Optional<User> findByTelegramLinkCode(String telegramLinkCode);
    Optional<User> findByTelegramChatId(Long telegramChatId);
    List<User> findByTelegramChatIdIsNotNull();

    // Referrals.
    Optional<User> findByReferralCode(String referralCode);
    long countByReferredBy(UUID referredBy);
}

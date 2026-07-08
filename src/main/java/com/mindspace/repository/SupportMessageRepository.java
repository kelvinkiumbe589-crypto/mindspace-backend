package com.mindspace.repository;

import com.mindspace.model.SupportMessage;
import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {
    List<SupportMessage> findByUserOrderByCreatedAtAsc(User user);
    List<SupportMessage> findByUserIdOrderByCreatedAtAsc(UUID userId);
    List<SupportMessage> findAllByOrderByCreatedAtAsc();

    // Guest (not-logged-in) conversations.
    List<SupportMessage> findByUserIsNullOrderByCreatedAtAsc();
    List<SupportMessage> findByGuestKeyOrderByCreatedAtAsc(String guestKey);

    // Admin replies a user hasn't opened and we haven't nudged them about yet.
    List<SupportMessage> findByFromAdminTrueAndSeenByUserFalseAndReminderSentAtIsNull();

    // Any admin reply we've never nudged about — used for a one-time catch-up.
    List<SupportMessage> findByFromAdminTrueAndReminderSentAtIsNull();
}

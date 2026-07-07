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
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    long countByCreatedAtAfter(LocalDateTime time);

    // Recipients of the daily mood reminder: ordinary members who haven't opted out.
    List<User> findByRoleAndMoodReminderEnabledTrue(User.Role role);
}

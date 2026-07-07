package com.mindspace.repository;

import com.mindspace.model.SessionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionMessageRepository extends JpaRepository<SessionMessage, UUID> {
    List<SessionMessage> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}

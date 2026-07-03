package com.mindspace.repository;

import com.mindspace.model.Booking;
import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByClientOrderByCreatedAtDesc(User client);
    List<Booking> findByTherapistOrderByScheduledAtAsc(User therapist);
    void deleteByTherapist(User therapist);
}

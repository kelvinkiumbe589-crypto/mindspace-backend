package com.mindspace.repository;

import com.mindspace.model.TherapistProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TherapistProfileRepository extends JpaRepository<TherapistProfile, UUID> {
    List<TherapistProfile> findAllByOrderByNameAsc();
    Optional<TherapistProfile> findByUserId(UUID userId);
}

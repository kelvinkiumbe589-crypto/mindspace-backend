package com.mindspace.repository;

import com.mindspace.model.MoodEntry;
import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MoodEntryRepository extends JpaRepository<MoodEntry, UUID> {
    List<MoodEntry> findByUserOrderByLoggedAtDesc(User user);

    // Bounded fetch for streak computation — 90 recent entries is more than enough
    // to measure a consecutive-day streak without loading a user's whole history.
    List<MoodEntry> findTop90ByUserOrderByLoggedAtDesc(User user);
}

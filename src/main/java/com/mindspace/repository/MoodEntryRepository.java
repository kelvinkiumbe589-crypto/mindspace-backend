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
}

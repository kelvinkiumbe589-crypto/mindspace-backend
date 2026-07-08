package com.mindspace.repository;

import com.mindspace.model.PageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PageViewRepository extends JpaRepository<PageView, UUID> {
    // All recent views; the service computes every window/aggregate from this in memory.
    List<PageView> findByCreatedAtAfter(LocalDateTime since);
}

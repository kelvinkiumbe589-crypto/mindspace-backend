package com.mindspace.repository;

import com.mindspace.model.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ForumPostRepository extends JpaRepository<ForumPost, UUID> {
    List<ForumPost> findAllByOrderByCreatedAtDesc();
    List<ForumPost> findByCategoryOrderByCreatedAtDesc(String category);

    // Atomic view-count bump — avoids a read-modify-write race under concurrent views.
    @Modifying
    @Query("update ForumPost p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int incrementViewCount(@Param("id") UUID id);
}

package com.mindspace.repository;

import com.mindspace.model.ForumPost;
import com.mindspace.model.ForumPostLike;
import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumPostLikeRepository extends JpaRepository<ForumPostLike, UUID> {
    long countByPost(ForumPost post);
    boolean existsByPostAndUser(ForumPost post, User user);
    Optional<ForumPostLike> findByPostAndUser(ForumPost post, User user);
    void deleteByPost(ForumPost post);
}

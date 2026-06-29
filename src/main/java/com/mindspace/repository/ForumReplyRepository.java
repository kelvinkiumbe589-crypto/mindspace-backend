package com.mindspace.repository;

import com.mindspace.model.ForumReply;
import com.mindspace.model.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ForumReplyRepository extends JpaRepository<ForumReply, UUID> {
    List<ForumReply> findByPostOrderByCreatedAtAsc(ForumPost post);
}

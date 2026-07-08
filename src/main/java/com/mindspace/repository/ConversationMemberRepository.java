package com.mindspace.repository;

import com.mindspace.model.Conversation;
import com.mindspace.model.ConversationMember;
import com.mindspace.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {
    List<ConversationMember> findByUser(User user);
    List<ConversationMember> findByConversation(Conversation conversation);
    Optional<ConversationMember> findByConversationAndUser(Conversation conversation, User user);
    boolean existsByConversationAndUser(Conversation conversation, User user);
    long countByConversation(Conversation conversation);
}

package com.mindspace.repository;

import com.mindspace.model.ChatMessage;
import com.mindspace.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);
    ChatMessage findTopByConversationOrderByCreatedAtDesc(Conversation conversation);
    // Unread = messages after the member's lastReadAt that they didn't send themselves.
    long countByConversationAndCreatedAtAfterAndSenderIdNot(Conversation conversation, LocalDateTime after, UUID senderId);
    void deleteByConversation(Conversation conversation);
}

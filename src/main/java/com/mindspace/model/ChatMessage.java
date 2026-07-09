package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single message inside a {@link Conversation}. Kept separate from SessionMessage
 * (which is tied to a booking) — this is member-to-member direct/group messaging.
 */
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_conv", columnList = "conversation_id, created_at"))
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Optional single attachment, stored as a data URL (no object storage).
    // mediaType is "image" or "video".
    @Column(name = "media_url", columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "media_type", length = 10)
    private String mediaType;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public ChatMessage() {}

    public ChatMessage(Conversation conversation, User sender, String content) {
        this.conversation = conversation;
        this.sender = sender;
        this.content = content;
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public String getContent() { return content; }
    public String getMediaUrl() { return mediaUrl; }
    public String getMediaType() { return mediaType; }
    public boolean isDeleted() { return deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setConversation(Conversation conversation) { this.conversation = conversation; }
    public void setSender(User sender) { this.sender = sender; }
    public void setContent(String content) { this.content = content; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

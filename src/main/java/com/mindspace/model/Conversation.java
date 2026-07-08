package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A messaging thread between members. DIRECT is a 1:1 chat (exactly two members);
 * GROUP is a named chat with any number of members. Contact details are never
 * exposed — members are identified only by their username.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    public enum Type { DIRECT, GROUP }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Type type;

    // Group name (null for direct chats — the UI shows the other member's name).
    @Column(length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // Bumped every time a message is sent, so the conversation list can sort by recency.
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Conversation() {}

    public UUID getId() { return id; }
    public Type getType() { return type; }
    public String getName() { return name; }
    public User getCreatedBy() { return createdBy; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setType(Type type) { this.type = type; }
    public void setName(String name) { this.name = name; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

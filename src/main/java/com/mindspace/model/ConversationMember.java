package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Membership of a user in a conversation. lastReadAt drives unread badges; muted
 * silences notifications for that member without leaving the conversation.
 */
@Entity
@Table(name = "conversation_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
public class ConversationMember {

    public enum Role { OWNER, MEMBER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.MEMBER;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean muted = false;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    public ConversationMember() {}

    public ConversationMember(Conversation conversation, User user, Role role) {
        this.conversation = conversation;
        this.user = user;
        this.role = role;
    }

    public UUID getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getUser() { return user; }
    public Role getRole() { return role; }
    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public boolean isMuted() { return muted; }
    public LocalDateTime getJoinedAt() { return joinedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setConversation(Conversation conversation) { this.conversation = conversation; }
    public void setUser(User user) { this.user = user; }
    public void setRole(Role role) { this.role = role; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }
    public void setMuted(boolean muted) { this.muted = muted; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}

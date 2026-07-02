package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "from_admin", nullable = false)
    private boolean fromAdmin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public SupportMessage() {}

    public SupportMessage(User user, String text, boolean fromAdmin) {
        this.user = user;
        this.text = text;
        this.fromAdmin = fromAdmin;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getText() { return text; }
    public boolean isFromAdmin() { return fromAdmin; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setUser(User user) { this.user = user; }
    public void setText(String text) { this.text = text; }
    public void setFromAdmin(boolean fromAdmin) { this.fromAdmin = fromAdmin; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One user blocking another. A block hides both users from each other in search and
 * prevents new direct messages in either direction.
 */
@Entity
@Table(name = "user_blocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public UserBlock() {}

    public UserBlock(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    public UUID getId() { return id; }
    public User getBlocker() { return blocker; }
    public User getBlocked() { return blocked; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setBlocker(User blocker) { this.blocker = blocker; }
    public void setBlocked(User blocked) { this.blocked = blocked; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

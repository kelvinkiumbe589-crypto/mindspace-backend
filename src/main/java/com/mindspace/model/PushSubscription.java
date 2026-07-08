package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A browser Web Push subscription for a user's device. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 500)
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public PushSubscription() {}

    public PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }

    public void setUser(User user) { this.user = user; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    public void setAuth(String auth) { this.auth = auth; }
}

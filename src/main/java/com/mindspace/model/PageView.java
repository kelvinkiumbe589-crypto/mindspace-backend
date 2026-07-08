package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One recorded visit to a page of the app. Deliberately anonymous: we store only the
 * path, an opaque per-browser session id (random, generated client-side) and the
 * referrer — never an IP address or anything that identifies the person. That's all
 * we need for traffic, top-pages and audience-trend analytics, and it keeps a mental
 * wellness product from logging sensitive personal data.
 */
@Entity
@Table(name = "page_views", indexes = {
        @Index(name = "idx_page_views_created_at", columnList = "created_at"),
        @Index(name = "idx_page_views_session", columnList = "session_id")
})
public class PageView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 300)
    private String path;

    @Column(length = 300)
    private String referrer;

    // Opaque random id kept in the visitor's browser — lets us count unique visitors
    // without knowing who they are.
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public PageView() {}

    public PageView(String path, String referrer, String sessionId) {
        this.path = path;
        this.referrer = referrer;
        this.sessionId = sessionId;
    }

    public UUID getId() { return id; }
    public String getPath() { return path; }
    public String getReferrer() { return referrer; }
    public String getSessionId() { return sessionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setPath(String path) { this.path = path; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "forum_posts")
public class ForumPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_anonymous")
    private Boolean isAnonymous = true;

    @Column(length = 50)
    private String category;

    // Optional single attachment, stored as a data URL (no object storage).
    // mediaType is "image" or "video".
    @Column(name = "media_url", columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "media_type", length = 10)
    private String mediaType;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ForumReply> replies;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public ForumPost() {}

    // Getters
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Boolean getIsAnonymous() { return isAnonymous; }
    public String getCategory() { return category; }
    public String getMediaUrl() { return mediaUrl; }
    public String getMediaType() { return mediaType; }
    public List<ForumReply> getReplies() { return replies; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setUser(User user) { this.user = user; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
    public void setCategory(String category) { this.category = category; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public void setReplies(List<ForumReply> replies) { this.replies = replies; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

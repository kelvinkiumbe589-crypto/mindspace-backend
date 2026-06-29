package com.mindspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ForumDto {

    // ── Create Post Request ───────────────────────────────────────
    public static class CreatePostRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be under 200 characters")
        private String title;

        @NotBlank(message = "Content is required")
        private String content;

        private Boolean isAnonymous = true;
        private String category = "general";

        public String getTitle() { return title; }
        public String getContent() { return content; }
        public Boolean getIsAnonymous() { return isAnonymous; }
        public String getCategory() { return category; }
        public void setTitle(String title) { this.title = title; }
        public void setContent(String content) { this.content = content; }
        public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
        public void setCategory(String category) { this.category = category; }
    }

    // ── Create Reply Request ──────────────────────────────────────
    public static class CreateReplyRequest {
        @NotBlank(message = "Content is required")
        private String content;

        private Boolean isAnonymous = true;

        public String getContent() { return content; }
        public Boolean getIsAnonymous() { return isAnonymous; }
        public void setContent(String content) { this.content = content; }
        public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
    }

    // ── Reply Response ────────────────────────────────────────────
    public static class ReplyResponse {
        private UUID id;
        private String content;
        private String author;
        private LocalDateTime createdAt;

        public UUID getId() { return id; }
        public String getContent() { return content; }
        public String getAuthor() { return author; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setId(UUID id) { this.id = id; }
        public void setContent(String content) { this.content = content; }
        public void setAuthor(String author) { this.author = author; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    // ── Post Response (list view) ─────────────────────────────────
    public static class PostResponse {
        private UUID id;
        private String title;
        private String content;
        private String author;
        private String category;
        private int replyCount;
        private LocalDateTime createdAt;

        public UUID getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getAuthor() { return author; }
        public String getCategory() { return category; }
        public int getReplyCount() { return replyCount; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setId(UUID id) { this.id = id; }
        public void setTitle(String title) { this.title = title; }
        public void setContent(String content) { this.content = content; }
        public void setAuthor(String author) { this.author = author; }
        public void setCategory(String category) { this.category = category; }
        public void setReplyCount(int replyCount) { this.replyCount = replyCount; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    // ── Post Detail Response (includes replies) ───────────────────
    public static class PostDetailResponse {
        private UUID id;
        private String title;
        private String content;
        private String author;
        private String category;
        private List<ReplyResponse> replies;
        private LocalDateTime createdAt;

        public UUID getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getAuthor() { return author; }
        public String getCategory() { return category; }
        public List<ReplyResponse> getReplies() { return replies; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setId(UUID id) { this.id = id; }
        public void setTitle(String title) { this.title = title; }
        public void setContent(String content) { this.content = content; }
        public void setAuthor(String author) { this.author = author; }
        public void setCategory(String category) { this.category = category; }
        public void setReplies(List<ReplyResponse> replies) { this.replies = replies; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}

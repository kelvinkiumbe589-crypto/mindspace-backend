package com.mindspace.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Email
    @NotBlank
    @Column(unique = true, nullable = false, length = 100)
    private String email;

    // Public messaging username (a "handle" like grace_ke). Distinct from the real
    // name in `username` — this is what other members search by, so real names stay
    // private. Nullable to allow adding the column to existing rows; backfilled on
    // startup and set for every new signup.
    @Column(unique = true, length = 20)
    private String handle;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    // Whether this user receives the daily "log your mood" reminder email.
    // columnDefinition sets a DB default so the column can be added to the existing
    // populated users table (ddl-auto=update) without a null-constraint violation.
    @Column(name = "mood_reminder_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean moodReminderEnabled = true;

    // The last calendar day (in the reminder timezone) we emailed this user a
    // reminder — used to guarantee at most one reminder per day even if the
    // trigger endpoint is called more than once.
    @Column(name = "last_reminder_date")
    private LocalDate lastReminderDate;

    // Telegram bot link: chat id once connected, and a short-lived code used to connect.
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_link_code", length = 40)
    private String telegramLinkCode;

    // Referral program: this user's own invite code, who invited them, and how
    // many AI deep-dive credits they've earned.
    @Column(name = "referral_code", length = 20, unique = true)
    private String referralCode;

    @Column(name = "referred_by")
    private UUID referredBy;

    @Column(name = "ai_credits", nullable = false, columnDefinition = "integer default 3")
    private int aiCredits = 3;

    // Profile photo. Stored as a small downscaled data URL. `avatarVisibility` is
    // 'private' (only the owner ever sees it — never exposed to others) or 'public'
    // (shown to people they chat with).
    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "avatar_visibility", length = 10, nullable = false, columnDefinition = "varchar(10) default 'private'")
    private String avatarVisibility = "private";

    // Presence: whether this user advertises their online/last-seen status, and the
    // last time they had a live chat socket open. Mutual — hiding yours also hides
    // others' from you (enforced client-side).
    @Column(name = "activity_visible", nullable = false, columnDefinition = "boolean default true")
    private boolean activityVisible = true;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Role {
        USER, THERAPIST, ADMIN
    }

    public User() {}

    // Getters
    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getHandle() { return handle; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isMoodReminderEnabled() { return moodReminderEnabled; }
    public LocalDate getLastReminderDate() { return lastReminderDate; }
    public Long getTelegramChatId() { return telegramChatId; }
    public String getTelegramLinkCode() { return telegramLinkCode; }
    public String getReferralCode() { return referralCode; }
    public UUID getReferredBy() { return referredBy; }
    public int getAiCredits() { return aiCredits; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getAvatarVisibility() { return avatarVisibility; }
    public boolean isActivityVisible() { return activityVisible; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setHandle(String handle) { this.handle = handle; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(Role role) { this.role = role; }
    public void setMoodReminderEnabled(boolean moodReminderEnabled) { this.moodReminderEnabled = moodReminderEnabled; }
    public void setLastReminderDate(LocalDate lastReminderDate) { this.lastReminderDate = lastReminderDate; }
    public void setTelegramChatId(Long telegramChatId) { this.telegramChatId = telegramChatId; }
    public void setTelegramLinkCode(String telegramLinkCode) { this.telegramLinkCode = telegramLinkCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public void setReferredBy(UUID referredBy) { this.referredBy = referredBy; }
    public void setAiCredits(int aiCredits) { this.aiCredits = aiCredits; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setAvatarVisibility(String avatarVisibility) { this.avatarVisibility = avatarVisibility; }
    public void setActivityVisible(boolean activityVisible) { this.activityVisible = activityVisible; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String username;
        private String email;
        private String passwordHash;
        private Role role = Role.USER;

        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder role(Role role) { this.role = role; return this; }

        public User build() {
            User u = new User();
            u.username = this.username;
            u.email = this.email;
            u.passwordHash = this.passwordHash;
            u.role = this.role;
            return u;
        }
    }
}

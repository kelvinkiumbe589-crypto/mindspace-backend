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

    // Null for guest (not-logged-in) conversations started from the landing page.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Guest identity (only set when user is null). guestKey is a stable synthetic id
    // derived from the guest's email so their messages group into one conversation.
    @Column(name = "guest_name", length = 120)
    private String guestName;

    @Column(name = "guest_email", length = 150)
    private String guestEmail;

    @Column(name = "guest_key", length = 60)
    private String guestKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "from_admin", nullable = false)
    private boolean fromAdmin;

    // For admin replies to a logged-in user: whether the user has opened their
    // chat since (used to nudge users who haven't come back to read the reply).
    @Column(name = "seen_by_user", nullable = false, columnDefinition = "boolean default false")
    private boolean seenByUser = false;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public SupportMessage() {}

    public SupportMessage(User user, String text, boolean fromAdmin) {
        this.user = user;
        this.text = text;
        this.fromAdmin = fromAdmin;
    }

    // Guest message (from/to a not-logged-in visitor).
    public SupportMessage(String guestName, String guestEmail, String guestKey, String text, boolean fromAdmin) {
        this.guestName = guestName;
        this.guestEmail = guestEmail;
        this.guestKey = guestKey;
        this.text = text;
        this.fromAdmin = fromAdmin;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getGuestName() { return guestName; }
    public String getGuestEmail() { return guestEmail; }
    public String getGuestKey() { return guestKey; }
    public String getText() { return text; }
    public boolean isFromAdmin() { return fromAdmin; }
    public boolean isSeenByUser() { return seenByUser; }
    public LocalDateTime getReminderSentAt() { return reminderSentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setUser(User user) { this.user = user; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }
    public void setGuestKey(String guestKey) { this.guestKey = guestKey; }
    public void setText(String text) { this.text = text; }
    public void setFromAdmin(boolean fromAdmin) { this.fromAdmin = fromAdmin; }
    public void setSeenByUser(boolean seenByUser) { this.seenByUser = seenByUser; }
    public void setReminderSentAt(LocalDateTime reminderSentAt) { this.reminderSentAt = reminderSentAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

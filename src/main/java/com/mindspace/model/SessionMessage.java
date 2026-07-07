package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A message in the private chat attached to a booking. The two parties (client and
 * therapist) coordinate here WITHOUT exchanging personal contact details — we store
 * only the sender's role, never revealing one party's email/phone to the other.
 */
@Entity
@Table(name = "session_messages")
public class SessionMessage {

    public enum Role { CLIENT, THERAPIST }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 12)
    private Role senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public SessionMessage() {}

    public SessionMessage(Booking booking, Role senderRole, String text) {
        this.booking = booking;
        this.senderRole = senderRole;
        this.text = text;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public Role getSenderRole() { return senderRole; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public void setSenderRole(Role senderRole) { this.senderRole = senderRole; }
    public void setText(String text) { this.text = text; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

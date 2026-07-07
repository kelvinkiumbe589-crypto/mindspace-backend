package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A booked therapy session. Lifecycle:
 * PENDING_PAYMENT -> AWAITING_APPROVAL (paid) -> APPROVED (therapist) -> DONE (therapist);
 * FAILED if payment didn't complete.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "therapist_id", nullable = false)
    private User therapist;

    @Column(nullable = false, length = 12)
    private String sessionType; // ONLINE | PHYSICAL

    @Column(nullable = false)
    private int amount; // KES

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING_PAYMENT;

    @Column(name = "order_tracking_id", length = 100)
    private String orderTrackingId;

    // For in-person sessions: a short code the client shows on arrival and the
    // therapist verifies. Generated when the booking is approved.
    @Column(name = "check_in_code", length = 8)
    private String checkInCode;

    @Column(name = "checked_in", nullable = false, columnDefinition = "boolean default false")
    private boolean checkedIn = false;

    @Column
    private Integer rating; // 1-5, set by the client after the session is DONE

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Status { PENDING_PAYMENT, AWAITING_APPROVAL, APPROVED, DONE, FAILED }

    public Booking() {}

    public UUID getId() { return id; }
    public User getClient() { return client; }
    public User getTherapist() { return therapist; }
    public String getSessionType() { return sessionType; }
    public int getAmount() { return amount; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public Status getStatus() { return status; }
    public String getOrderTrackingId() { return orderTrackingId; }
    public String getCheckInCode() { return checkInCode; }
    public boolean isCheckedIn() { return checkedIn; }
    public Integer getRating() { return rating; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setClient(User client) { this.client = client; }
    public void setTherapist(User therapist) { this.therapist = therapist; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }
    public void setAmount(int amount) { this.amount = amount; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setStatus(Status status) { this.status = status; }
    public void setOrderTrackingId(String orderTrackingId) { this.orderTrackingId = orderTrackingId; }
    public void setCheckInCode(String checkInCode) { this.checkInCode = checkInCode; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }
    public void setRating(Integer rating) { this.rating = rating; }
}

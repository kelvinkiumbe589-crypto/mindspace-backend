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

    // For ONLINE sessions: the total call time (minutes) the client paid for,
    // chosen at booking. amount = online rate * (durationMinutes / 60).
    @Column(name = "duration_minutes", nullable = false, columnDefinition = "int default 60")
    private int durationMinutes = 60;

    // Connected-time budget bookkeeping for the video call. Only time both peers
    // are actually connected is deducted; gaps between calls don't count.
    @Column(name = "consumed_seconds", nullable = false, columnDefinition = "int default 0")
    private int consumedSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_state", length = 12)
    private CallState callState = CallState.NONE;

    // When the current LIVE segment began (both peers connected). Null when idle.
    @Column(name = "segment_started_at")
    private LocalDateTime segmentStartedAt;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING_PAYMENT;

    @Column(name = "order_tracking_id", length = 100)
    private String orderTrackingId;

    // For in-person sessions: a short code the client shows on arrival and the
    // therapist verifies. Generated when the booking is approved.
    // The client's phone (captured at booking) — used for WhatsApp session messages.
    @Column(name = "client_phone", length = 30)
    private String clientPhone;

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

    /** Online-call lifecycle: NONE (idle), RINGING (invite sent), LIVE (both on call), ENDED (budget spent). */
    public enum CallState { NONE, RINGING, LIVE, ENDED }

    public Booking() {}

    public UUID getId() { return id; }
    public User getClient() { return client; }
    public User getTherapist() { return therapist; }
    public String getSessionType() { return sessionType; }
    public int getAmount() { return amount; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getConsumedSeconds() { return consumedSeconds; }
    public CallState getCallState() { return callState == null ? CallState.NONE : callState; }
    public LocalDateTime getSegmentStartedAt() { return segmentStartedAt; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public Status getStatus() { return status; }
    public String getOrderTrackingId() { return orderTrackingId; }
    public String getClientPhone() { return clientPhone; }
    public String getCheckInCode() { return checkInCode; }
    public boolean isCheckedIn() { return checkedIn; }
    public Integer getRating() { return rating; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setClient(User client) { this.client = client; }
    public void setTherapist(User therapist) { this.therapist = therapist; }
    public void setSessionType(String sessionType) { this.sessionType = sessionType; }
    public void setAmount(int amount) { this.amount = amount; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setConsumedSeconds(int consumedSeconds) { this.consumedSeconds = consumedSeconds; }
    public void setCallState(CallState callState) { this.callState = callState; }
    public void setSegmentStartedAt(LocalDateTime segmentStartedAt) { this.segmentStartedAt = segmentStartedAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setStatus(Status status) { this.status = status; }
    public void setOrderTrackingId(String orderTrackingId) { this.orderTrackingId = orderTrackingId; }
    public void setClientPhone(String clientPhone) { this.clientPhone = clientPhone; }
    public void setCheckInCode(String checkInCode) { this.checkInCode = checkInCode; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }
    public void setRating(Integer rating) { this.rating = rating; }
}

package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A therapist's request to withdraw earnings. Admin pays it out and marks it PAID. */
@Entity
@Table(name = "withdrawals")
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "therapist_id", nullable = false)
    private User therapist;

    @Column(nullable = false)
    private int grossAmount;   // taken from available balance

    @Column(nullable = false)
    private int commission;    // platform cut

    @Column(nullable = false)
    private int netAmount;     // paid to the therapist

    @Column(length = 30)
    private String phone;      // where to send it (M-Pesa)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public enum Status { REQUESTED, PAID, REJECTED }

    public Withdrawal() {}

    public UUID getId() { return id; }
    public User getTherapist() { return therapist; }
    public int getGrossAmount() { return grossAmount; }
    public int getCommission() { return commission; }
    public int getNetAmount() { return netAmount; }
    public String getPhone() { return phone; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }

    public void setTherapist(User therapist) { this.therapist = therapist; }
    public void setGrossAmount(int grossAmount) { this.grossAmount = grossAmount; }
    public void setCommission(int commission) { this.commission = commission; }
    public void setNetAmount(int netAmount) { this.netAmount = netAmount; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setStatus(Status status) { this.status = status; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}

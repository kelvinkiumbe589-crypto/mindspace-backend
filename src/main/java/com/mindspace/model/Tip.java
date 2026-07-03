package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A "buy me a coffee" style contribution to the platform. */
@Entity
@Table(name = "tips")
public class Tip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 80)
    private String name;

    @Column(length = 280)
    private String message;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "order_tracking_id", length = 100)
    private String orderTrackingId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Status { PENDING, PAID }

    public Tip() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getMessage() { return message; }
    public int getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getOrderTrackingId() { return orderTrackingId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setMessage(String message) { this.message = message; }
    public void setAmount(int amount) { this.amount = amount; }
    public void setStatus(Status status) { this.status = status; }
    public void setOrderTrackingId(String orderTrackingId) { this.orderTrackingId = orderTrackingId; }
}

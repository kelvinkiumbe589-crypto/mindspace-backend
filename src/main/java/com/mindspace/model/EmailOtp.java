package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A one-time verification code sent to a user's email for 2-step verification.
 * For REGISTER, the pending account details (username + password hash) are
 * stashed here so the user row is only created once the code is confirmed.
 */
@Entity
@Table(name = "email_otps", indexes = {
        @Index(name = "idx_email_otp_email_purpose", columnList = "email, purpose")
})
public class EmailOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    // Pending account details (REGISTER only)
    @Column(length = 50)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Purpose { REGISTER, LOGIN }

    public EmailOtp() {}

    public EmailOtp(String email, String code, Purpose purpose, String username, String passwordHash, LocalDateTime expiresAt) {
        this.email = email;
        this.code = code;
        this.purpose = purpose;
        this.username = username;
        this.passwordHash = passwordHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getCode() { return code; }
    public Purpose getPurpose() { return purpose; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setAttempts(int attempts) { this.attempts = attempts; }
    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }
}

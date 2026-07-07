package com.mindspace.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Directory + booking profile for a therapist. Backed by a User (role THERAPIST) for login. */
@Entity
@Table(name = "therapist_profiles")
public class TherapistProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 120)
    private String title;

    @Column(length = 255)
    private String specialties; // comma-separated

    @Column(name = "price_online", nullable = false)
    private int priceOnline = 2000; // KES

    // In-person session price (KES). Null → fall back to a multiple of the online price.
    @Column(name = "price_physical")
    private Integer pricePhysical;

    // Where in-person sessions happen. The address is a business/clinic location the
    // client sees after their booking is approved — never a personal contact.
    @Column(name = "practice_address", length = 500)
    private String practiceAddress;

    // Google Maps links can be very long, so store as TEXT (no length limit).
    @Column(name = "practice_map_url", columnDefinition = "TEXT")
    private String practiceMapUrl;

    @Column(name = "practice_notes", length = 500)
    private String practiceNotes;

    @Column(length = 8)
    private String initials;

    @Column(length = 16)
    private String color = "#534AB7";

    @Column(length = 500)
    private String bio;

    @Column(nullable = false)
    private boolean available = true;

    @Column(nullable = false)
    private double rating = 5.0;

    @Column(nullable = false)
    private int reviews = 0;

    // Availability the therapist sets themselves.
    @Column(name = "available_days", length = 40)
    private String availableDays = "1,2,3,4,5"; // 0=Sun..6=Sat

    @Column(name = "available_slots", length = 255)
    private String availableSlots = "09:00,10:00,11:00,14:00,15:00,16:00";

    // Where the therapist wants withdrawals sent.
    @Column(name = "payout_method", length = 10)
    private String payoutMethod = "MPESA"; // MPESA | BANK

    @Column(name = "payout_mpesa", length = 20)
    private String payoutMpesa;

    @Column(name = "payout_bank_name", length = 80)
    private String payoutBankName;

    @Column(name = "payout_bank_account", length = 40)
    private String payoutBankAccount;

    @Column(name = "payout_account_name", length = 80)
    private String payoutAccountName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public TherapistProfile() {}

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getSpecialties() { return specialties; }
    public int getPriceOnline() { return priceOnline; }
    public Integer getPricePhysical() { return pricePhysical; }
    public String getPracticeAddress() { return practiceAddress; }
    public String getPracticeMapUrl() { return practiceMapUrl; }
    public String getPracticeNotes() { return practiceNotes; }
    public String getInitials() { return initials; }
    public String getColor() { return color; }
    public String getBio() { return bio; }
    public boolean isAvailable() { return available; }
    public double getRating() { return rating; }
    public int getReviews() { return reviews; }
    public String getAvailableDays() { return availableDays; }
    public String getAvailableSlots() { return availableSlots; }
    public String getPayoutMethod() { return payoutMethod; }
    public String getPayoutMpesa() { return payoutMpesa; }
    public String getPayoutBankName() { return payoutBankName; }
    public String getPayoutBankAccount() { return payoutBankAccount; }
    public String getPayoutAccountName() { return payoutAccountName; }

    public void setUser(User user) { this.user = user; }
    public void setName(String name) { this.name = name; }
    public void setTitle(String title) { this.title = title; }
    public void setSpecialties(String specialties) { this.specialties = specialties; }
    public void setPriceOnline(int priceOnline) { this.priceOnline = priceOnline; }
    public void setPricePhysical(Integer pricePhysical) { this.pricePhysical = pricePhysical; }
    public void setPracticeAddress(String practiceAddress) { this.practiceAddress = practiceAddress; }
    public void setPracticeMapUrl(String practiceMapUrl) { this.practiceMapUrl = practiceMapUrl; }
    public void setPracticeNotes(String practiceNotes) { this.practiceNotes = practiceNotes; }
    public void setInitials(String initials) { this.initials = initials; }
    public void setColor(String color) { this.color = color; }
    public void setBio(String bio) { this.bio = bio; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setRating(double rating) { this.rating = rating; }
    public void setReviews(int reviews) { this.reviews = reviews; }
    public void setAvailableDays(String availableDays) { this.availableDays = availableDays; }
    public void setAvailableSlots(String availableSlots) { this.availableSlots = availableSlots; }
    public void setPayoutMethod(String payoutMethod) { this.payoutMethod = payoutMethod; }
    public void setPayoutMpesa(String payoutMpesa) { this.payoutMpesa = payoutMpesa; }
    public void setPayoutBankName(String payoutBankName) { this.payoutBankName = payoutBankName; }
    public void setPayoutBankAccount(String payoutBankAccount) { this.payoutBankAccount = payoutBankAccount; }
    public void setPayoutAccountName(String payoutAccountName) { this.payoutAccountName = payoutAccountName; }
}

package com.mindspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TherapistDto {

    /** Admin creates a therapist account + directory profile. */
    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Invalid email address")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        private String title;
        private String specialties; // comma-separated
        private int priceOnline;
        private String bio;

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getTitle() { return title; }
        public String getSpecialties() { return specialties; }
        public int getPriceOnline() { return priceOnline; }
        public String getBio() { return bio; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
        public void setTitle(String title) { this.title = title; }
        public void setSpecialties(String specialties) { this.specialties = specialties; }
        public void setPriceOnline(int priceOnline) { this.priceOnline = priceOnline; }
        public void setBio(String bio) { this.bio = bio; }
    }

    /** Admin edits a therapist. Password/email optional (only changed if provided). */
    public static class UpdateRequest {
        @NotBlank(message = "Name is required")
        private String name;

        private String email;      // optional — change login email
        private String password;   // optional — reset password
        private String title;
        private String specialties;
        private int priceOnline;
        private Integer pricePhysical;
        private String practiceAddress;
        private String practiceMapUrl;
        private String practiceNotes;
        private String bio;
        private Boolean available;

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getTitle() { return title; }
        public String getSpecialties() { return specialties; }
        public int getPriceOnline() { return priceOnline; }
        public Integer getPricePhysical() { return pricePhysical; }
        public String getPracticeAddress() { return practiceAddress; }
        public String getPracticeMapUrl() { return practiceMapUrl; }
        public String getPracticeNotes() { return practiceNotes; }
        public String getBio() { return bio; }
        public Boolean getAvailable() { return available; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
        public void setTitle(String title) { this.title = title; }
        public void setSpecialties(String specialties) { this.specialties = specialties; }
        public void setPriceOnline(int priceOnline) { this.priceOnline = priceOnline; }
        public void setPricePhysical(Integer pricePhysical) { this.pricePhysical = pricePhysical; }
        public void setPracticeAddress(String practiceAddress) { this.practiceAddress = practiceAddress; }
        public void setPracticeMapUrl(String practiceMapUrl) { this.practiceMapUrl = practiceMapUrl; }
        public void setPracticeNotes(String practiceNotes) { this.practiceNotes = practiceNotes; }
        public void setBio(String bio) { this.bio = bio; }
        public void setAvailable(Boolean available) { this.available = available; }
    }

    /** A therapist updates their OWN profile (price + availability). */
    public static class SelfUpdateRequest {
        private int priceOnline;
        private Integer pricePhysical;
        private String practiceAddress;
        private String practiceMapUrl;
        private String practiceNotes;
        private String title;
        private String specialties;
        private String bio;
        private Boolean available;
        private List<Integer> availableDays;  // 0=Sun..6=Sat
        private List<String> availableSlots;  // "HH:MM"
        private String payoutMethod;
        private String payoutMpesa;
        private String payoutBankName;
        private String payoutBankAccount;
        private String payoutAccountName;

        public int getPriceOnline() { return priceOnline; }
        public Integer getPricePhysical() { return pricePhysical; }
        public String getPracticeAddress() { return practiceAddress; }
        public String getPracticeMapUrl() { return practiceMapUrl; }
        public String getPracticeNotes() { return practiceNotes; }
        public String getTitle() { return title; }
        public String getSpecialties() { return specialties; }
        public String getBio() { return bio; }
        public Boolean getAvailable() { return available; }
        public List<Integer> getAvailableDays() { return availableDays; }
        public List<String> getAvailableSlots() { return availableSlots; }
        public String getPayoutMethod() { return payoutMethod; }
        public String getPayoutMpesa() { return payoutMpesa; }
        public String getPayoutBankName() { return payoutBankName; }
        public String getPayoutBankAccount() { return payoutBankAccount; }
        public String getPayoutAccountName() { return payoutAccountName; }
        public void setPriceOnline(int priceOnline) { this.priceOnline = priceOnline; }
        public void setPricePhysical(Integer pricePhysical) { this.pricePhysical = pricePhysical; }
        public void setPracticeAddress(String practiceAddress) { this.practiceAddress = practiceAddress; }
        public void setPracticeMapUrl(String practiceMapUrl) { this.practiceMapUrl = practiceMapUrl; }
        public void setPracticeNotes(String practiceNotes) { this.practiceNotes = practiceNotes; }
        public void setTitle(String title) { this.title = title; }
        public void setSpecialties(String specialties) { this.specialties = specialties; }
        public void setBio(String bio) { this.bio = bio; }
        public void setAvailable(Boolean available) { this.available = available; }
        public void setAvailableDays(List<Integer> availableDays) { this.availableDays = availableDays; }
        public void setAvailableSlots(List<String> availableSlots) { this.availableSlots = availableSlots; }
        public void setPayoutMethod(String payoutMethod) { this.payoutMethod = payoutMethod; }
        public void setPayoutMpesa(String payoutMpesa) { this.payoutMpesa = payoutMpesa; }
        public void setPayoutBankName(String payoutBankName) { this.payoutBankName = payoutBankName; }
        public void setPayoutBankAccount(String payoutBankAccount) { this.payoutBankAccount = payoutBankAccount; }
        public void setPayoutAccountName(String payoutAccountName) { this.payoutAccountName = payoutAccountName; }
    }

    /** Public directory view of a therapist. */
    public static class Response {
        public String id;          // profile id
        public String userId;      // login/account id (booking target)
        public String name;
        public String title;
        public List<String> specialties;
        public int priceOnline;
        public int pricePhysical;          // effective price (override or 1.5x fallback)
        public Integer pricePhysicalSet;   // raw override (null = auto) — for the therapist's own form
        public String practiceAddress;
        public String practiceMapUrl;
        public String practiceNotes;
        public String initials;
        public String color;
        public String bio;
        public boolean available;
        public double rating;
        public int reviews;
        public String email;
        public List<Integer> availableDays;
        public List<String> availableSlots;
        // Populated only for the therapist's own profile view (never in the public directory).
        public String payoutMethod;
        public String payoutMpesa;
        public String payoutBankName;
        public String payoutBankAccount;
        public String payoutAccountName;
    }
}

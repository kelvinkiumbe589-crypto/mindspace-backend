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
        private String bio;
        private Boolean available;

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public String getTitle() { return title; }
        public String getSpecialties() { return specialties; }
        public int getPriceOnline() { return priceOnline; }
        public String getBio() { return bio; }
        public Boolean getAvailable() { return available; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setPassword(String password) { this.password = password; }
        public void setTitle(String title) { this.title = title; }
        public void setSpecialties(String specialties) { this.specialties = specialties; }
        public void setPriceOnline(int priceOnline) { this.priceOnline = priceOnline; }
        public void setBio(String bio) { this.bio = bio; }
        public void setAvailable(Boolean available) { this.available = available; }
    }

    /** Public directory view of a therapist. */
    public static class Response {
        public String id;          // profile id
        public String userId;      // login/account id (booking target)
        public String name;
        public String title;
        public List<String> specialties;
        public int priceOnline;
        public int pricePhysical;
        public String initials;
        public String color;
        public String bio;
        public boolean available;
        public double rating;
        public int reviews;
        public String email;
    }
}

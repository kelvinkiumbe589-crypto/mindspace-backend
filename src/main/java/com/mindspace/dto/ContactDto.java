package com.mindspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ContactDto {

    public static class ContactRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "A valid email is required")
        private String email;

        private String phone;

        @NotBlank(message = "Message is required")
        private String message;

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public String getMessage() { return message; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setPhone(String phone) { this.phone = phone; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ContactResponse {
        private String status;   // "sent" | "unavailable"
        private String detail;

        public ContactResponse() {}
        public ContactResponse(String status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        public String getStatus() { return status; }
        public String getDetail() { return detail; }
        public void setStatus(String status) { this.status = status; }
        public void setDetail(String detail) { this.detail = detail; }
    }
}

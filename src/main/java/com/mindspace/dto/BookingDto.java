package com.mindspace.dto;

import jakarta.validation.constraints.NotBlank;

public class BookingDto {

    /** A client creates a booking (before payment). Amount is computed server-side. */
    public static class CreateRequest {
        @NotBlank(message = "Therapist is required")
        private String therapistId;

        @NotBlank(message = "Session type is required")
        private String sessionType; // ONLINE | PHYSICAL

        private String scheduledAt;  // ISO-8601, e.g. 2026-07-10T15:00
        private String phone;        // client's phone, for WhatsApp session messages
        private Integer durationMinutes; // ONLINE only: total call time paid for (e.g. 60, 120)

        public String getTherapistId() { return therapistId; }
        public String getSessionType() { return sessionType; }
        public String getScheduledAt() { return scheduledAt; }
        public String getPhone() { return phone; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setTherapistId(String therapistId) { this.therapistId = therapistId; }
        public void setSessionType(String sessionType) { this.sessionType = sessionType; }
        public void setScheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; }
        public void setPhone(String phone) { this.phone = phone; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    }

    public static class PaidRequest {
        private String orderTrackingId;
        public String getOrderTrackingId() { return orderTrackingId; }
        public void setOrderTrackingId(String orderTrackingId) { this.orderTrackingId = orderTrackingId; }
    }

    public static class RateRequest {
        private int rating; // 1-5
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
    }

    public static class Response {
        public String id;
        public String therapistId;
        public String therapistName;
        public String clientName;
        public String clientEmail;
        public String sessionType;
        public int amount;
        // ONLINE call time budget (minutes paid for) + live call bookkeeping.
        public int durationMinutes;
        public int remainingSeconds;
        public String callState; // NONE | RINGING | LIVE | ENDED
        public String scheduledAt;
        public String status;
        public String createdAt;
        public Integer rating;
        // In-person details — populated only for PHYSICAL bookings once approved.
        public String practiceAddress;
        public String practiceMapUrl;
        public String practiceNotes;
        public String checkInCode;
        public boolean checkedIn;
    }
}

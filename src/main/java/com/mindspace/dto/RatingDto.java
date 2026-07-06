package com.mindspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.UUID;

public class RatingDto {

    /** A user submits (or updates) their rating. */
    public static class SubmitRequest {
        @Min(value = 1, message = "Please choose 1 to 5 stars")
        @Max(value = 5, message = "Please choose 1 to 5 stars")
        private int stars;
        private String comment;

        public int getStars() { return stars; }
        public String getComment() { return comment; }
        public void setStars(int stars) { this.stars = stars; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class Response {
        public UUID id;
        public int stars;
        public String comment;
        public String userName;
        public String userEmail;
        public LocalDateTime createdAt;
    }

    /** Aggregate stats for the admin dashboard. */
    public static class Summary {
        public double average;   // e.g. 4.3
        public int count;        // total ratings
        public int[] distribution = new int[5]; // [# of 1-star, ... , # of 5-star]
    }
}

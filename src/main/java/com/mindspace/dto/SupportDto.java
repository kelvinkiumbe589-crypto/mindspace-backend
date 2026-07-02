package com.mindspace.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.UUID;

public class SupportDto {

    public static class SendRequest {
        @NotBlank(message = "Message text is required")
        private String text;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class MessageResponse {
        private UUID id;
        private String text;
        private boolean fromAdmin;
        private LocalDateTime createdAt;

        public MessageResponse(UUID id, String text, boolean fromAdmin, LocalDateTime createdAt) {
            this.id = id;
            this.text = text;
            this.fromAdmin = fromAdmin;
            this.createdAt = createdAt;
        }

        public UUID getId() { return id; }
        public String getText() { return text; }
        public boolean isFromAdmin() { return fromAdmin; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    // Summary row for the admin inbox
    public static class Conversation {
        private UUID userId;
        private String username;
        private String email;
        private String lastMessage;
        private boolean lastFromAdmin;
        private LocalDateTime lastAt;
        private int messageCount;

        public Conversation(UUID userId, String username, String email, String lastMessage,
                            boolean lastFromAdmin, LocalDateTime lastAt, int messageCount) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.lastMessage = lastMessage;
            this.lastFromAdmin = lastFromAdmin;
            this.lastAt = lastAt;
            this.messageCount = messageCount;
        }

        public UUID getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getLastMessage() { return lastMessage; }
        public boolean isLastFromAdmin() { return lastFromAdmin; }
        public LocalDateTime getLastAt() { return lastAt; }
        public int getMessageCount() { return messageCount; }
    }
}

package com.mindspace.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class SessionDto {

    public static class SendRequest {
        @NotBlank(message = "Message text is required")
        private String text;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class MessageResponse {
        private UUID id;
        private String text;
        private String senderRole; // CLIENT | THERAPIST
        private boolean mine;      // true if the caller sent it
        private LocalDateTime createdAt;

        public MessageResponse(UUID id, String text, String senderRole, boolean mine, LocalDateTime createdAt) {
            this.id = id;
            this.text = text;
            this.senderRole = senderRole;
            this.mine = mine;
            this.createdAt = createdAt;
        }

        public UUID getId() { return id; }
        public String getText() { return text; }
        public String getSenderRole() { return senderRole; }
        public boolean isMine() { return mine; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    // Chat context for the header — counterparty shown by name only, never contact.
    public static class Thread {
        private String bookingId;
        private String counterpartyName;
        private String sessionType;
        private String status;
        private boolean canChat;
        private List<MessageResponse> messages;

        public Thread(String bookingId, String counterpartyName, String sessionType,
                      String status, boolean canChat, List<MessageResponse> messages) {
            this.bookingId = bookingId;
            this.counterpartyName = counterpartyName;
            this.sessionType = sessionType;
            this.status = status;
            this.canChat = canChat;
            this.messages = messages;
        }

        public String getBookingId() { return bookingId; }
        public String getCounterpartyName() { return counterpartyName; }
        public String getSessionType() { return sessionType; }
        public String getStatus() { return status; }
        public boolean isCanChat() { return canChat; }
        public List<MessageResponse> getMessages() { return messages; }
    }
}

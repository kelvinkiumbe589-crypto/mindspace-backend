package com.mindspace.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationDto {

    public static class Item {
        private UUID id;
        private String type;
        private String message;
        private String link;
        private boolean read;
        private LocalDateTime createdAt;

        public Item(UUID id, String type, String message, String link, boolean read, LocalDateTime createdAt) {
            this.id = id;
            this.type = type;
            this.message = message;
            this.link = link;
            this.read = read;
            this.createdAt = createdAt;
        }

        public UUID getId() { return id; }
        public String getType() { return type; }
        public String getMessage() { return message; }
        public String getLink() { return link; }
        public boolean isRead() { return read; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}

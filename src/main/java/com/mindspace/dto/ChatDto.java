package com.mindspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ChatDto {

    // ── Requests ──────────────────────────────────────────────────
    public static class DirectRequest {
        private UUID userId;
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
    }

    public static class GroupRequest {
        @NotBlank(message = "Group name is required")
        @Size(max = 120, message = "Group name must be under 120 characters")
        private String name;
        @NotEmpty(message = "Add at least one member")
        private List<UUID> memberIds;
        public String getName() { return name; }
        public List<UUID> getMemberIds() { return memberIds; }
        public void setName(String name) { this.name = name; }
        public void setMemberIds(List<UUID> memberIds) { this.memberIds = memberIds; }
    }

    public static class SendRequest {
        @NotBlank(message = "Message cannot be empty")
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class AddMembersRequest {
        @NotEmpty(message = "Select at least one member")
        private List<UUID> memberIds;
        public List<UUID> getMemberIds() { return memberIds; }
        public void setMemberIds(List<UUID> memberIds) { this.memberIds = memberIds; }
    }

    public static class UserRequest {
        private UUID userId;
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
    }

    public static class MuteRequest {
        private boolean muted;
        public boolean isMuted() { return muted; }
        public void setMuted(boolean muted) { this.muted = muted; }
    }

    public static class ReportRequest {
        private UUID userId;
        private UUID conversationId; // optional context
        @Size(max = 1000)
        private String reason;
        public UUID getUserId() { return userId; }
        public UUID getConversationId() { return conversationId; }
        public String getReason() { return reason; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // ── Responses ─────────────────────────────────────────────────
    public static class UserResult {
        private UUID id;
        private String username;
        public UserResult(UUID id, String username) { this.id = id; this.username = username; }
        public UUID getId() { return id; }
        public String getUsername() { return username; }
    }

    public static class MemberInfo {
        private UUID userId;
        private String username;
        private String role;
        public MemberInfo(UUID userId, String username, String role) {
            this.userId = userId; this.username = username; this.role = role;
        }
        public UUID getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
    }

    public static class MessageInfo {
        private UUID id;
        private UUID senderId;
        private String senderName;
        private String content;
        private boolean mine;
        private boolean deleted;
        private LocalDateTime createdAt;
        public MessageInfo(UUID id, UUID senderId, String senderName, String content,
                           boolean mine, boolean deleted, LocalDateTime createdAt) {
            this.id = id; this.senderId = senderId; this.senderName = senderName;
            this.content = content; this.mine = mine; this.deleted = deleted; this.createdAt = createdAt;
        }
        public UUID getId() { return id; }
        public UUID getSenderId() { return senderId; }
        public String getSenderName() { return senderName; }
        public String getContent() { return content; }
        public boolean isMine() { return mine; }
        public boolean isDeleted() { return deleted; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public static class ConversationSummary {
        private UUID id;
        private String type;         // DIRECT | GROUP
        private String title;        // group name, or the other member's username
        private String avatar;       // initial for the avatar circle
        private UUID otherUserId;    // for DIRECT chats (null for groups)
        private int memberCount;
        private String lastMessage;
        private LocalDateTime lastMessageAt;
        private long unread;
        private boolean muted;

        public UUID getId() { return id; }
        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getAvatar() { return avatar; }
        public UUID getOtherUserId() { return otherUserId; }
        public int getMemberCount() { return memberCount; }
        public String getLastMessage() { return lastMessage; }
        public LocalDateTime getLastMessageAt() { return lastMessageAt; }
        public long getUnread() { return unread; }
        public boolean isMuted() { return muted; }
        public void setId(UUID id) { this.id = id; }
        public void setType(String type) { this.type = type; }
        public void setTitle(String title) { this.title = title; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public void setOtherUserId(UUID otherUserId) { this.otherUserId = otherUserId; }
        public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
        public void setUnread(long unread) { this.unread = unread; }
        public void setMuted(boolean muted) { this.muted = muted; }
    }

    public static class ConversationDetail {
        private UUID id;
        private String type;
        private String title;
        private boolean owner;          // is the caller the group owner?
        private List<MemberInfo> members;
        private List<MessageInfo> messages;

        public UUID getId() { return id; }
        public String getType() { return type; }
        public String getTitle() { return title; }
        public boolean isOwner() { return owner; }
        public List<MemberInfo> getMembers() { return members; }
        public List<MessageInfo> getMessages() { return messages; }
        public void setId(UUID id) { this.id = id; }
        public void setType(String type) { this.type = type; }
        public void setTitle(String title) { this.title = title; }
        public void setOwner(boolean owner) { this.owner = owner; }
        public void setMembers(List<MemberInfo> members) { this.members = members; }
        public void setMessages(List<MessageInfo> messages) { this.messages = messages; }
    }
}

package com.mindspace.dto;

public class AiChatDto {

    public static class Turn {
        private String role; // "user" or "ai"/"assistant"
        private String text;

        public String getRole() { return role; }
        public String getText() { return text; }
        public void setRole(String role) { this.role = role; }
        public void setText(String text) { this.text = text; }
    }

    public static class ChatRequest {
        private String moodContext;
        private String question;
        private java.util.List<Turn> history; // recent prior turns, oldest first

        public String getMoodContext() { return moodContext; }
        public String getQuestion() { return question; }
        public java.util.List<Turn> getHistory() { return history; }
        public void setMoodContext(String moodContext) { this.moodContext = moodContext; }
        public void setQuestion(String question) { this.question = question; }
        public void setHistory(java.util.List<Turn> history) { this.history = history; }
    }

    public static class ChatResponse {
        private String reply;

        public ChatResponse() {}
        public ChatResponse(String reply) { this.reply = reply; }

        public String getReply() { return reply; }
        public void setReply(String reply) { this.reply = reply; }
    }
}

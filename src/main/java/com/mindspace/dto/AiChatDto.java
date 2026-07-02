package com.mindspace.dto;

public class AiChatDto {

    public static class ChatRequest {
        private String moodContext;
        private String question;

        public String getMoodContext() { return moodContext; }
        public String getQuestion() { return question; }
        public void setMoodContext(String moodContext) { this.moodContext = moodContext; }
        public void setQuestion(String question) { this.question = question; }
    }

    public static class ChatResponse {
        private String reply;

        public ChatResponse() {}
        public ChatResponse(String reply) { this.reply = reply; }

        public String getReply() { return reply; }
        public void setReply(String reply) { this.reply = reply; }
    }
}

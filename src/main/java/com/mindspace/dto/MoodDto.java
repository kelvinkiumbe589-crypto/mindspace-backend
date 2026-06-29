package com.mindspace.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public class MoodDto {

    public static class MoodRequest {
        @NotNull(message = "Mood score is required")
        @Min(value = 1, message = "Mood score must be at least 1")
        @Max(value = 10, message = "Mood score must be at most 10")
        private Integer moodScore;

        private String emotions;
        private String journalText;

        public Integer getMoodScore() { return moodScore; }
        public String getEmotions() { return emotions; }
        public String getJournalText() { return journalText; }
        public void setMoodScore(Integer moodScore) { this.moodScore = moodScore; }
        public void setEmotions(String emotions) { this.emotions = emotions; }
        public void setJournalText(String journalText) { this.journalText = journalText; }
    }

    public static class MoodResponse {
        private UUID id;
        private Integer moodScore;
        private String emotions;
        private String journalText;
        private String aiInsight;
        private LocalDateTime loggedAt;

        public UUID getId() { return id; }
        public Integer getMoodScore() { return moodScore; }
        public String getEmotions() { return emotions; }
        public String getJournalText() { return journalText; }
        public String getAiInsight() { return aiInsight; }
        public LocalDateTime getLoggedAt() { return loggedAt; }
        public void setId(UUID id) { this.id = id; }
        public void setMoodScore(Integer moodScore) { this.moodScore = moodScore; }
        public void setEmotions(String emotions) { this.emotions = emotions; }
        public void setJournalText(String journalText) { this.journalText = journalText; }
        public void setAiInsight(String aiInsight) { this.aiInsight = aiInsight; }
        public void setLoggedAt(LocalDateTime loggedAt) { this.loggedAt = loggedAt; }
    }
}

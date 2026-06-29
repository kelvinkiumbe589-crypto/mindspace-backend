package com.mindspace.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mood_entries")
public class MoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Min(1) @Max(10)
    @Column(name = "mood_score", nullable = false)
    private Integer moodScore;

    @Column(name = "emotions")
    private String emotions;

    @Column(name = "journal_text", columnDefinition = "TEXT")
    private String journalText;

    @Column(name = "ai_insight", columnDefinition = "TEXT")
    private String aiInsight;

    @CreationTimestamp
    @Column(name = "logged_at", updatable = false)
    private LocalDateTime loggedAt;

    public MoodEntry() {}

    // Getters
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Integer getMoodScore() { return moodScore; }
    public String getEmotions() { return emotions; }
    public String getJournalText() { return journalText; }
    public String getAiInsight() { return aiInsight; }
    public LocalDateTime getLoggedAt() { return loggedAt; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setUser(User user) { this.user = user; }
    public void setMoodScore(Integer moodScore) { this.moodScore = moodScore; }
    public void setEmotions(String emotions) { this.emotions = emotions; }
    public void setJournalText(String journalText) { this.journalText = journalText; }
    public void setAiInsight(String aiInsight) { this.aiInsight = aiInsight; }
    public void setLoggedAt(LocalDateTime loggedAt) { this.loggedAt = loggedAt; }

    // Builder
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private User user;
        private Integer moodScore;
        private String emotions;
        private String journalText;

        public Builder user(User user) { this.user = user; return this; }
        public Builder moodScore(Integer moodScore) { this.moodScore = moodScore; return this; }
        public Builder emotions(String emotions) { this.emotions = emotions; return this; }
        public Builder journalText(String journalText) { this.journalText = journalText; return this; }

        public MoodEntry build() {
            MoodEntry e = new MoodEntry();
            e.user = this.user;
            e.moodScore = this.moodScore;
            e.emotions = this.emotions;
            e.journalText = this.journalText;
            return e;
        }
    }
}

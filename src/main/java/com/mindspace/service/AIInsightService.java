package com.mindspace.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.mindspace.dto.MoodDto;
import com.mindspace.model.MoodEntry;
import com.mindspace.repository.MoodEntryRepository;

@Service
public class AIInsightService {

    private final MoodEntryRepository moodEntryRepository;
    private final RestClient restClient;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    public AIInsightService(MoodEntryRepository moodEntryRepository) {
        this.moodEntryRepository = moodEntryRepository;
        this.restClient = RestClient.create();
    }

    /**
     * Stateless assistant used by the dashboard. Given a summary of the user's
     * recent moods and an optional question, returns a Gemini reply. When the
     * question is blank it produces a proactive wellness insight instead.
     */
    public String assistantReply(String moodContext, String question) {
        String context = (moodContext == null || moodContext.isBlank())
                ? "The user has not logged any moods yet."
                : moodContext.length() > 4000 ? moodContext.substring(0, 4000) : moodContext;

        String prompt;
        if (question == null || question.isBlank()) {
            prompt = "You are a compassionate mental wellness assistant for the MindSpace app. "
                    + "Based on the user's recent mood entries below, give a short, warm, personalised insight "
                    + "(3-4 sentences). Point out one pattern you notice, offer one practical tip, and end with "
                    + "encouragement. Do not diagnose or replace professional help.\n\nRecent moods:\n" + context;
        } else {
            String q = question.length() > 1000 ? question.substring(0, 1000) : question;
            prompt = "You are a compassionate mental wellness assistant for the MindSpace app. "
                    + "Use the user's recent mood entries as context, then answer their question in a warm, "
                    + "supportive and practical way (keep it concise). If the question is a crisis or medical issue, "
                    + "gently encourage them to seek professional help. Do not diagnose.\n\n"
                    + "Recent moods:\n" + context + "\n\nUser question: " + q;
        }
        return callGeminiAPI(prompt);
    }

    public MoodDto.MoodResponse generateInsight(UUID moodId) {
        MoodEntry entry = moodEntryRepository.findById(moodId)
                .orElseThrow(() -> new IllegalArgumentException("Mood entry not found"));

        String prompt = buildPrompt(entry);
        String insight = callGeminiAPI(prompt);

        entry.setAiInsight(insight);
        MoodEntry saved = moodEntryRepository.save(entry);

        MoodDto.MoodResponse response = new MoodDto.MoodResponse();
        response.setId(saved.getId());
        response.setMoodScore(saved.getMoodScore());
        response.setEmotions(saved.getEmotions());
        response.setJournalText(saved.getJournalText());
        response.setAiInsight(saved.getAiInsight());
        response.setLoggedAt(saved.getLoggedAt());
        return response;
    }

    private String buildPrompt(MoodEntry entry) {
        return String.format(
            "You are a compassionate mental wellness assistant. " +
            "A user logged the following mood entry:\n\n" +
            "Mood Score: %d/10\n" +
            "Emotions: %s\n" +
            "Journal: %s\n\n" +
            "Please provide a short, warm, and helpful wellness insight (3-4 sentences). " +
            "Acknowledge their feelings, offer one practical tip, and end with encouragement. " +
            "Do not diagnose or replace professional help.",
            entry.getMoodScore(),
            entry.getEmotions() != null ? entry.getEmotions() : "not specified",
            entry.getJournalText() != null ? entry.getJournalText() : "no journal entry"
        );
    }

    @SuppressWarnings("unchecked")
    private String callGeminiAPI(String prompt) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                         "gemini-2.5-flash:generateContent?key=" + geminiApiKey;

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", prompt)
                    ))
                )
            );

            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> content =
                        (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts =
                        (List<Map<String, Object>>) content.get("parts");
                    return (String) parts.get(0).get("text");
                }
            }
            return "Thank you for sharing your feelings today. Keep going — every step forward matters.";

        } catch (Exception e) {
            System.out.println("Gemini API error: " + e.getMessage());
            return "Thank you for logging your mood today. Remember to be kind to yourself.";
        }
    }
}
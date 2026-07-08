package com.mindspace.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.mindspace.dto.AiChatDto;
import com.mindspace.dto.MoodDto;
import com.mindspace.model.MoodEntry;
import com.mindspace.repository.MoodEntryRepository;

@Service
public class AIInsightService {

    private final MoodEntryRepository moodEntryRepository;
    private final RestClient restClient;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    // Optional: when set, use Groq (works from cloud servers where Google blocks us).
    @Value("${app.groq.api-key:}")
    private String groqApiKey;

    public AIInsightService(MoodEntryRepository moodEntryRepository) {
        this.moodEntryRepository = moodEntryRepository;
        this.restClient = RestClient.create();
    }

    // Grounds the assistant in what MindSpace actually is and does, so it guides
    // users to in-app features instead of inventing generic advice or naming rivals.
    private static final String SYSTEM =
        "You are the built-in AI assistant inside MindSpace, a mental wellness app. Speak as part of "
        + "MindSpace (\"we\"/\"in the app\"). What MindSpace offers: daily mood journaling with emotion tags; "
        + "AI wellness insights; mood trend charts; an anonymous peer support community forum; and a directory "
        + "of LICENSED THERAPISTS that users can BOOK directly in the app — either an online video session "
        + "held inside MindSpace, or an in-person session — paid securely via M-Pesa, card or bank. "
        + "RULES: (1) MindSpace DOES let users book therapists. When someone asks how to book, tell them to open "
        + "the \"Find a Therapist\" page in the app, pick a therapist, choose a time, and pay — then they can "
        + "message/video-call the therapist in the app. (2) NEVER recommend or name any other app or website "
        + "(e.g. BetterHelp, Talkspace) — only point users to MindSpace's own features. (3) Be warm, concise and "
        + "supportive; do not diagnose or replace professional care. (4) For an emergency or crisis, gently "
        + "suggest contacting a local emergency line or crisis service.";

    /**
     * Stateless assistant used by the dashboard. Given a summary of the user's
     * recent moods and an optional question, returns a Gemini reply. When the
     * question is blank it produces a proactive wellness insight instead.
     */
    public String assistantReply(String moodContext, String question, List<AiChatDto.Turn> history) {
        String context = (moodContext == null || moodContext.isBlank())
                ? "The user has not logged any moods yet."
                : moodContext.length() > 4000 ? moodContext.substring(0, 4000) : moodContext;

        // Blank question → one-off proactive insight.
        if (question == null || question.isBlank()) {
            String prompt = SYSTEM + "\n\n"
                    + "Based on the user's recent mood entries below, give a short, warm, personalised insight "
                    + "(3-4 sentences). Point out one pattern you notice, offer one practical tip, and end with "
                    + "encouragement.\n\nRecent moods:\n" + context;
            return generate(prompt);
        }

        // Conversation: system + recent turns + the new message, so follow-ups
        // like "yeah" have context. We deliberately do NOT feed the mood summary
        // here — it made the assistant open every reply by recapping the user's
        // moods. Mood analysis lives in the separate proactive "insight" feature.
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                SYSTEM + "\n\nAnswer the user's latest message directly and keep the conversation flowing "
                + "naturally. Be brief. Do NOT open your reply by describing or recapping the user's mood, "
                + "tiredness or symptoms unless they explicitly bring it up in their latest message."));
        if (history != null) {
            int start = Math.max(0, history.size() - 8); // keep the last ~8 turns
            for (int i = start; i < history.size(); i++) {
                AiChatDto.Turn t = history.get(i);
                if (t == null || t.getText() == null || t.getText().isBlank()) continue;
                String role = "user".equalsIgnoreCase(t.getRole()) ? "user" : "assistant";
                String txt = t.getText().length() > 1000 ? t.getText().substring(0, 1000) : t.getText();
                messages.add(Map.of("role", role, "content", txt));
            }
        }
        String q = question.length() > 1000 ? question.substring(0, 1000) : question;
        messages.add(Map.of("role", "user", "content", q));
        return generateChat(messages);
    }

    /** A richer, longer personalised analysis — the "AI Deep-Dive" perk. */
    public String deepDive(String moodContext) {
        String context = (moodContext == null || moodContext.isBlank())
                ? "The user has not logged many moods yet."
                : moodContext.length() > 5000 ? moodContext.substring(0, 5000) : moodContext;
        String prompt = SYSTEM + "\n\n"
                + "Give the user a thorough, warm \"deep-dive\" reflection on their recent moods below "
                + "(about 3 short paragraphs). Cover: (1) the patterns and possible triggers you notice, "
                + "(2) what seems to lift them up, and (3) a concrete, gentle 3-step plan for the week ahead. "
                + "Encouraging and human, not clinical. Do not diagnose.\n\nRecent moods:\n" + context;
        return generate(prompt);
    }

    /** Use Groq if configured (reliable from servers), otherwise Gemini. */
    private String generate(String prompt) {
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            return callGroq(prompt);
        }
        return callGeminiAPI(prompt);
    }

    /** Multi-turn variant: messages are {role: system|user|assistant, content}. */
    private String generateChat(List<Map<String, String>> messages) {
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            return callGroqChat(messages);
        }
        return callGeminiChat(messages);
    }

    @SuppressWarnings("unchecked")
    private String callGroqChat(List<Map<String, String>> messages) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", messages);
            Map<String, Object> response = restClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null && message.get("content") != null) return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            System.out.println("Groq chat error: " + e.getMessage());
        }
        return "I'm here with you. Could you tell me a little more?";
    }

    @SuppressWarnings("unchecked")
    private String callGeminiChat(List<Map<String, String>> messages) {
        try {
            String systemText = messages.stream()
                    .filter(m -> "system".equals(m.get("role")))
                    .map(m -> m.get("content")).findFirst().orElse("");
            List<Map<String, Object>> contents = new ArrayList<>();
            for (Map<String, String> m : messages) {
                String role = m.get("role");
                if ("system".equals(role)) continue;
                String gRole = "user".equals(role) ? "user" : "model";
                contents.add(Map.of("role", gRole, "parts", List.of(Map.of("text", m.get("content")))));
            }
            Map<String, Object> requestBody = Map.of(
                    "system_instruction", Map.of("parts", List.of(Map.of("text", systemText))),
                    "contents", contents);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.5-flash:generateContent?key=" + geminiApiKey;
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (compatible; MindSpace/1.0)")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    return (String) parts.get(0).get("text");
                }
            }
        } catch (Exception e) {
            System.out.println("Gemini chat error: " + e.getMessage());
        }
        return "I'm here with you. Could you tell me a little more?";
    }

    public MoodDto.MoodResponse generateInsight(UUID moodId) {
        MoodEntry entry = moodEntryRepository.findById(moodId)
                .orElseThrow(() -> new IllegalArgumentException("Mood entry not found"));

        String prompt = buildPrompt(entry);
        String insight = generate(prompt);

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
                    // Google's edge returns a 403 "robot" page to Java's default
                    // User-Agent when the call comes from a server/data-center; a
                    // normal UA avoids that block.
                    .header("User-Agent", "Mozilla/5.0 (compatible; MindSpace/1.0)")
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

    @SuppressWarnings("unchecked")
    private String callGroq(String prompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            Map<String, Object> response = restClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null && message.get("content") != null) {
                        return (String) message.get("content");
                    }
                }
            }
            return "Thank you for sharing your feelings today. Keep going — every step forward matters.";

        } catch (Exception e) {
            System.out.println("Groq API error: " + e.getMessage());
            return "Thank you for logging your mood today. Remember to be kind to yourself.";
        }
    }
}
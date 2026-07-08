package com.mindspace.service;

import com.mindspace.model.MoodEntry;
import com.mindspace.model.User;
import com.mindspace.repository.MoodEntryRepository;
import com.mindspace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MindSpace Telegram bot. Users connect their account from Settings, then log
 * moods by texting the bot (e.g. "mood 7 feeling calm"). Runs on webhooks.
 */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final Pattern SCORE = Pattern.compile("\\b(10|[1-9])\\b");

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Value("${app.telegram.bot-username:}")
    private String botUsername;

    private final UserRepository userRepository;
    private final MoodEntryRepository moodEntryRepository;
    private final RestClient rest;

    public TelegramService(UserRepository userRepository, MoodEntryRepository moodEntryRepository) {
        this.userRepository = userRepository;
        this.moodEntryRepository = moodEntryRepository;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.rest = RestClient.builder().requestFactory(f).build();
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank();
    }

    /** Create a one-time connect link the user opens to bind their Telegram chat. */
    @Transactional
    public String linkUrl(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        String code = UUID.randomUUID().toString().replace("-", "");
        u.setTelegramLinkCode(code);
        userRepository.save(u);
        String bot = botUsername == null ? "" : botUsername.replace("@", "");
        return "https://t.me/" + bot + "?start=" + code;
    }

    /** Handle one Telegram update (from the webhook). Never throws. */
    @Transactional
    public void handleUpdate(Map<String, Object> update) {
        try {
            Object msgObj = update.get("message");
            if (!(msgObj instanceof Map)) return;
            @SuppressWarnings("unchecked") Map<String, Object> message = (Map<String, Object>) msgObj;
            @SuppressWarnings("unchecked") Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            if (chat == null) return;
            long chatId = ((Number) chat.get("id")).longValue();
            String text = message.get("text") == null ? "" : message.get("text").toString().trim();
            if (text.isEmpty()) return;

            if (text.startsWith("/start")) {
                String[] parts = text.split("\\s+", 2);
                if (parts.length == 2 && !parts[1].isBlank()) linkAccount(parts[1].trim(), chatId);
                else sendMessage(chatId, "👋 Welcome to MindSpace! To connect your account, open the MindSpace app → Settings → Connect Telegram.");
                return;
            }
            if (text.equalsIgnoreCase("/help")) {
                sendMessage(chatId, "Log your mood by texting a score 1–10 and a note, e.g.\n\n\"mood 7 feeling calm\"\n\nConnect your account first from MindSpace → Settings → Connect Telegram.");
                return;
            }

            User user = userRepository.findByTelegramChatId(chatId).orElse(null);
            if (user == null) {
                sendMessage(chatId, "Please connect your account first: open MindSpace → Settings → Connect Telegram.");
                return;
            }
            Integer score = firstScore(text);
            if (score != null) {
                String note = text.replaceFirst("(?i)mood", "").replaceFirst("\\b(10|[1-9])\\b", "").trim();
                moodEntryRepository.save(MoodEntry.builder().user(user).moodScore(score)
                        .journalText(note.isEmpty() ? null : note).build());
                sendMessage(chatId, "✅ Logged mood " + score + "/10" + (note.isEmpty() ? "" : " — \"" + note + "\"") + ". Take care 💜");
            } else {
                sendMessage(chatId, "Send a score 1–10 and a note to log your mood, e.g. \"mood 7 feeling calm\". Type /help for more.");
            }
        } catch (Exception e) {
            log.warn("Telegram update handling failed: {}", e.getMessage());
        }
    }

    private void linkAccount(String code, long chatId) {
        User u = userRepository.findByTelegramLinkCode(code).orElse(null);
        if (u == null) {
            sendMessage(chatId, "This connect link is invalid or already used. Open MindSpace → Settings → Connect Telegram to try again.");
            return;
        }
        u.setTelegramChatId(chatId);
        u.setTelegramLinkCode(null);
        userRepository.save(u);
        String name = u.getUsername() == null ? "there" : u.getUsername().split("\\s+")[0];
        sendMessage(chatId, "✅ Connected, " + name + "! You can now log your mood here — just text e.g. \"mood 7 feeling hopeful\".");
    }

    private Integer firstScore(String text) {
        Matcher m = SCORE.matcher(text);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= 10) return n;
        }
        return null;
    }

    public void sendMessage(long chatId, String text) {
        if (!isConfigured()) return;
        try {
            rest.post()
                    .uri("https://api.telegram.org/bot" + botToken + "/sendMessage")
                    .header("Content-Type", "application/json")
                    .body(Map.of("chat_id", chatId, "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Telegram sendMessage failed: {}", e.getMessage());
        }
    }
}

package com.mindspace.service;

import com.mindspace.model.MoodEntry;
import com.mindspace.model.User;
import com.mindspace.repository.MoodEntryRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends a daily "log your mood" reminder email to members, personalised with their
 * current streak. Triggered by an external cron hitting the internal endpoint (Render's
 * free tier sleeps, so an in-app timer wouldn't fire reliably).
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final UserRepository userRepository;
    private final MoodEntryRepository moodEntryRepository;
    private final MailService mailService;
    private final JwtUtil jwtUtil;
    private final SupportService supportService;
    private final TelegramService telegramService;

    // Only one batch may run at a time (guards against overlapping cron calls).
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.reminder.timezone:Africa/Nairobi}")
    private String timezone;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    public ReminderService(UserRepository userRepository, MoodEntryRepository moodEntryRepository,
                           MailService mailService, JwtUtil jwtUtil, SupportService supportService,
                           TelegramService telegramService) {
        this.userRepository = userRepository;
        this.moodEntryRepository = moodEntryRepository;
        this.mailService = mailService;
        this.jwtUtil = jwtUtil;
        this.supportService = supportService;
        this.telegramService = telegramService;
    }

    /**
     * Kick off the reminder batch on a background thread and return immediately, so the
     * cron caller isn't held for the whole send. Returns false if a batch is already running.
     */
    public boolean triggerAsync() {
        if (!running.compareAndSet(false, true)) {
            log.info("Reminder batch already running — ignoring duplicate trigger");
            return false;
        }
        Thread t = new Thread(() -> {
            try {
                runBatch();
            } finally {
                running.set(false);
            }
        }, "daily-reminders");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void runBatch() {
        ZoneId zone = resolveZone();
        LocalDate today = LocalDate.now(zone);

        // Telegram-connected users get their daily check-in there (runs first, so the
        // shared once-a-day guard then skips their reminder email).
        try {
            int tg = telegramService.sendDailyCheckins();
            if (tg > 0) log.info("Telegram daily check-ins sent: {}", tg);
        } catch (Exception e) {
            log.warn("Telegram check-ins failed: {}", e.getMessage());
        }

        List<User> recipients = userRepository.findByRoleAndMoodReminderEnabledTrue(User.Role.USER);

        int sent = 0, skipped = 0, failed = 0;
        for (User user : recipients) {
            // At most one reminder per user per day, even across repeated triggers.
            if (today.equals(user.getLastReminderDate())) {
                skipped++;
                continue;
            }
            try {
                Streak s = computeStreak(user, zone, today);
                String subject = subjectFor(s);
                String body = bodyFor(user, s);
                boolean ok = mailService.send(user.getEmail(), subject, body);
                // Mark as reminded regardless, so a dead mailbox doesn't get retried all day.
                user.setLastReminderDate(today);
                userRepository.save(user);
                if (ok) sent++; else failed++;
            } catch (Exception e) {
                failed++;
                log.warn("Reminder failed for {}: {}", user.getEmail(), e.getMessage());
            }
        }
        log.info("Daily reminders done — sent={}, skipped={}, failed={}, total={}",
                sent, skipped, failed, recipients.size());

        // Also nudge users who haven't opened a support reply.
        try {
            supportService.sendSupportReplyReminders();
        } catch (Exception e) {
            log.warn("Support reply reminders failed: {}", e.getMessage());
        }
    }

    private ZoneId resolveZone() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid app.reminder.timezone '{}', defaulting to UTC", timezone);
            return ZoneOffset.UTC;
        }
    }

    // ── Streak ────────────────────────────────────────────────────
    private record Streak(int days, boolean loggedToday) {}

    private Streak computeStreak(User user, ZoneId zone, LocalDate today) {
        List<MoodEntry> entries = moodEntryRepository.findTop90ByUserOrderByLoggedAtDesc(user);
        Set<LocalDate> days = new HashSet<>();
        for (MoodEntry e : entries) {
            if (e.getLoggedAt() == null) continue;
            // loggedAt is stored in the server's zone (UTC in the cloud); normalise to the
            // reminder zone so "today"/streak boundaries line up with the recipient's day.
            LocalDate d = e.getLoggedAt().atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate();
            days.add(d);
        }
        boolean loggedToday = days.contains(today);
        LocalDate cursor = loggedToday ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return new Streak(streak, loggedToday);
    }

    // ── Message ───────────────────────────────────────────────────
    private String subjectFor(Streak s) {
        if (s.loggedToday()) return "You're on a " + s.days() + "-day streak 🔥";
        if (s.days() > 0) return "Keep your " + s.days() + "-day streak alive 🔥";
        return "How are you feeling today? 🌱";
    }

    private String bodyFor(User user, Streak s) {
        String name = (user.getUsername() == null || user.getUsername().isBlank()) ? "there" : user.getUsername();
        String cta = frontendUrl + "/mood-journal";

        String opener;
        if (s.loggedToday()) {
            opener = "Nice work — you've already checked in today, and you're on a " + s.days() +
                    "-day streak. 🔥 See you again tomorrow to keep it going.";
        } else if (s.days() > 0) {
            opener = "You're on a " + s.days() + "-day streak. 🔥 Take a few seconds to log how " +
                    "you're feeling today so you don't lose it.";
        } else {
            opener = "Take a few seconds to log how you're feeling today — it's the first step to " +
                    "understanding your patterns, and the start of a new streak. 🌱";
        }

        return "Hi " + name + ",\n\n" +
                opener + "\n\n" +
                "Log today's mood: " + cta + "\n\n" +
                "Take care of yourself,\n" +
                "The MindSpace team\n\n" +
                "—\n" +
                "Don't want daily reminders? Unsubscribe: " + unsubscribeLink(user.getEmail()) + "\n";
    }

    private String unsubscribeLink(String email) {
        String token = URLEncoder.encode(jwtUtil.generateUnsubscribeToken(email), StandardCharsets.UTF_8);
        return backendUrl + "/api/reminders/unsubscribe?token=" + token;
    }

    // ── Preferences / unsubscribe ─────────────────────────────────
    public boolean getPreference(String email) {
        return userRepository.findByEmail(email).map(User::isMoodReminderEnabled).orElse(true);
    }

    public void setPreference(String email, boolean enabled) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setMoodReminderEnabled(enabled);
            userRepository.save(u);
        });
    }

    /** Returns true if the token was valid and the user was unsubscribed. */
    public boolean unsubscribe(String token) {
        String email = jwtUtil.parseUnsubscribeEmail(token);
        if (email == null) return false;
        setPreference(email, false);
        return true;
    }
}

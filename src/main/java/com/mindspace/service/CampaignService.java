package com.mindspace.service;

import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import com.mindspace.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Onboarding "drip" campaign: a fixed sequence of promotional emails sent in the
 * days after signup (day 0's welcome is handled inline by {@link MailService} at
 * registration; this covers the follow-ups). Like {@link ReminderService} it's
 * driven by an external cron hitting an internal endpoint, and each email carries
 * a one-click unsubscribe that flips the user's marketing opt-out.
 *
 * Safety properties:
 *  - Each step is sent at most once (guarded by user.lastCampaignStep).
 *  - At most one step per user per run (so a cron outage can't blast a backlog).
 *  - A step is only sent within a small catch-up window after its target day, so
 *    users who signed up long before this feature existed are never back-filled.
 */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    // How many extra days after a step's target day it stays sendable — lets a
    // missed cron run still deliver, without spamming users far past onboarding.
    private static final long CATCHUP_WINDOW_DAYS = 3;

    private final UserRepository userRepository;
    private final MailService mailService;
    private final JwtUtil jwtUtil;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    public CampaignService(UserRepository userRepository, MailService mailService, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.jwtUtil = jwtUtil;
    }

    // ── The drip sequence ─────────────────────────────────────────
    private record Step(int step, int day, String subject, Function<Step, String> body) {}

    private List<Step> steps() {
        return List.of(
                new Step(1, 3, "Your first insight is waiting 🌱", this::bodyStep1),
                new Step(2, 7, "You're not alone — meet the community 💬", this::bodyStep2),
                new Step(3, 14, "Talk to someone who gets it 🫂", this::bodyStep3)
        );
    }

    // ── Trigger (called by an external cron, e.g. cron-job.org) ────
    public boolean triggerAsync() {
        if (!running.compareAndSet(false, true)) {
            log.info("Campaign batch already running — ignoring duplicate trigger");
            return false;
        }
        Thread t = new Thread(() -> {
            try {
                runBatch();
            } finally {
                running.set(false);
            }
        }, "onboarding-campaign");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void runBatch() {
        List<Step> steps = steps();
        List<User> recipients = userRepository.findByRoleAndMarketingEmailEnabledTrue(User.Role.USER);
        LocalDateTime now = LocalDateTime.now();

        int sent = 0, failed = 0;
        for (User user : recipients) {
            if (user.getCreatedAt() == null || user.getEmail() == null || user.getEmail().isBlank()) continue;
            long daysSince = ChronoUnit.DAYS.between(user.getCreatedAt(), now);

            // First not-yet-sent step whose target day has arrived and is still inside
            // its catch-up window. Sending at most one step keeps runs gentle.
            Step due = null;
            for (Step s : steps) {
                if (s.step() <= user.getLastCampaignStep()) continue;
                if (daysSince >= s.day() && daysSince <= s.day() + CATCHUP_WINDOW_DAYS) {
                    due = s;
                    break;
                }
                // Past this step's window without sending it — skip it permanently so we
                // don't deliver stale onboarding mail, but keep checking later steps.
                if (daysSince > s.day() + CATCHUP_WINDOW_DAYS && s.step() > user.getLastCampaignStep()) {
                    user.setLastCampaignStep(s.step());
                }
            }

            if (due == null) {
                // Persist any "skipped past window" advances so we don't re-evaluate them.
                if (user.getLastCampaignStep() > 0) userRepository.save(user);
                continue;
            }

            try {
                boolean ok = mailService.send(user.getEmail(), due.subject(), due.body().apply(due).replace("{{name}}", displayName(user)).replace("{{unsub}}", unsubscribeLink(user.getEmail())));
                user.setLastCampaignStep(due.step()); // mark sent even on failure, so a dead mailbox isn't retried forever
                userRepository.save(user);
                if (ok) sent++; else failed++;
            } catch (Exception e) {
                failed++;
                log.warn("Campaign step {} failed for {}: {}", due.step(), user.getEmail(), e.getMessage());
            }
        }
        log.info("Onboarding campaign done — sent={}, failed={}, eligible={}", sent, failed, recipients.size());
    }

    // ── Email bodies ──────────────────────────────────────────────
    // {{name}} and {{unsub}} are substituted per-recipient in runBatch.
    private String bodyStep1(Step s) {
        return "Hi {{name}},\n\n" +
                "You joined MindSpace a few days ago — here's the quickest way to feel the value:\n\n" +
                "  1. Log how you're feeling today (it takes 20 seconds)\n" +
                "  2. Tap \"Generate insight\" to get a gentle, AI-powered reflection on your patterns\n\n" +
                "The more you check in, the sharper your insights get.\n\n" +
                "Log today's mood: " + frontendUrl + "/dashboard\n\n" +
                signature();
    }

    private String bodyStep2(Step s) {
        return "Hi {{name}},\n\n" +
                "Tracking your mood is powerful — but you don't have to do it alone.\n\n" +
                "The MindSpace community forum is a safe, anonymous-friendly place to share wins, " +
                "ask for support, and read how others are getting through the same things. You can " +
                "even post a photo or video.\n\n" +
                "See what people are talking about: " + frontendUrl + "/community-forum\n\n" +
                signature();
    }

    private String bodyStep3(Step s) {
        return "Hi {{name}},\n\n" +
                "Sometimes self-tracking and peer support aren't enough — and that's completely okay.\n\n" +
                "When you're ready, MindSpace connects you with licensed therapists you can book and " +
                "message securely, right from the app. No waiting rooms, no judgement.\n\n" +
                "Browse therapists: " + frontendUrl + "/find-a-therapist\n\n" +
                signature();
    }

    private String signature() {
        return "Take care of yourself,\n" +
                "The MindSpace team\n\n" +
                "—\n" +
                "Don't want tips like these? Unsubscribe: {{unsub}}\n";
    }

    private String displayName(User u) {
        return (u.getUsername() == null || u.getUsername().isBlank()) ? "there" : u.getUsername();
    }

    private String unsubscribeLink(String email) {
        String token = URLEncoder.encode(jwtUtil.generateUnsubscribeToken(email), StandardCharsets.UTF_8);
        return backendUrl + "/api/campaigns/unsubscribe?token=" + token;
    }

    // ── Preferences / unsubscribe ─────────────────────────────────
    public boolean getPreference(String email) {
        return userRepository.findByEmail(email).map(User::isMarketingEmailEnabled).orElse(true);
    }

    public void setPreference(String email, boolean enabled) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setMarketingEmailEnabled(enabled);
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

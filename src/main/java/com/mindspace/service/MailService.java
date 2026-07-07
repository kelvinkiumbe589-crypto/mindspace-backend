package com.mindspace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Sends plain-text email. Prefers Brevo's HTTPS API (works from hosts that block
 * SMTP, e.g. Render's free tier); falls back to SMTP (JavaMailSender) locally, and
 * finally just logs when nothing is configured.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender; // null when spring.mail.* isn't configured
    private final RestClient rest;

    @Value("${spring.mail.username:}")
    private String smtpFrom;

    @Value("${app.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.brevo.sender-email:}")
    private String brevoSenderEmail;

    @Value("${app.brevo.sender-name:MindSpace}")
    private String brevoSenderName;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        // Bounded SMTP timeouts so a blocked port (e.g. Render blocks 587) fails fast and
        // falls back cleanly, instead of hanging the request/worker thread for minutes.
        if (this.mailSender instanceof JavaMailSenderImpl impl) {
            Properties props = impl.getJavaMailProperties();
            props.put("mail.smtp.connectiontimeout", "6000");
            props.put("mail.smtp.timeout", "6000");
            props.put("mail.smtp.writetimeout", "6000");
        }
        // Bounded timeouts so a slow/blocked mail provider can never hang a request thread.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(12).toMillis());
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return hasBrevo() || hasSmtp();
    }

    private boolean hasBrevo() { return brevoApiKey != null && !brevoApiKey.isBlank(); }
    private boolean hasSmtp() { return mailSender != null && smtpFrom != null && !smtpFrom.isBlank(); }

    private String senderEmail() {
        if (brevoSenderEmail != null && !brevoSenderEmail.isBlank()) return brevoSenderEmail;
        if (smtpFrom != null && !smtpFrom.isBlank()) return smtpFrom;
        return "no-reply@mindspace.app";
    }

    /** Best-effort send. Returns true if a provider accepted it. */
    public boolean send(String to, String subject, String text) {
        if (hasBrevo()) {
            try {
                sendViaBrevo(to, subject, text);
                return true;
            } catch (Exception e) {
                log.warn("Brevo email send failed: {}", e.getMessage());
            }
        }
        if (hasSmtp()) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setTo(to);
                mail.setFrom(smtpFrom);
                mail.setSubject(subject);
                mail.setText(text);
                mailSender.send(mail);
                return true;
            } catch (Exception e) {
                log.warn("SMTP email send failed: {}", e.getMessage());
            }
        }
        log.warn("No mail provider configured — would have emailed {} : {}", to, subject);
        return false;
    }

    /**
     * Fire-and-forget welcome email for a newly registered user. Runs on a daemon
     * thread so email latency never delays the signup response, and never throws —
     * a mail failure must not break account creation.
     */
    public void sendWelcomeAsync(String toEmail, String username) {
        if (toEmail == null || toEmail.isBlank()) return;
        String name = (username == null || username.isBlank()) ? "there" : username;
        String subject = "Welcome to MindSpace 🧠";
        String text =
                "Hi " + name + ",\n\n" +
                "Welcome to MindSpace — we're really glad you're here.\n\n" +
                "MindSpace is your calm, private space to track your mood, understand your " +
                "patterns, get gentle AI insights, connect with a supportive community, and " +
                "reach a licensed therapist whenever you need one.\n\n" +
                "A good way to start:\n" +
                "  1. Log how you're feeling today\n" +
                "  2. Ask the AI assistant for a quick insight\n" +
                "  3. Explore the community forum\n\n" +
                "Take care of yourself,\n" +
                "The MindSpace team\n";
        Thread t = new Thread(() -> {
            try {
                send(toEmail, subject, text);
            } catch (Exception ignored) {
                // never let a mail failure affect the created account
            }
        }, "welcome-email");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Fire-and-forget email telling a post author someone replied. Runs on a daemon
     * thread and never throws, so it can't delay or break saving the reply.
     */
    public void sendForumReplyAsync(String toEmail, String authorName, String replierName,
                                    String postTitle, String replyText, String link) {
        if (toEmail == null || toEmail.isBlank()) return;
        String name = (authorName == null || authorName.isBlank()) ? "there" : authorName;
        String who = (replierName == null || replierName.isBlank()) ? "Someone" : replierName;
        String snippet = replyText == null ? "" : (replyText.length() > 300 ? replyText.substring(0, 300) + "…" : replyText);
        String subject = who + " replied to your post on MindSpace";
        String text =
                "Hi " + name + ",\n\n" +
                who + " replied to your post \"" + postTitle + "\":\n\n" +
                "\"" + snippet + "\"\n\n" +
                "Read and reply: " + link + "\n\n" +
                "— The MindSpace team\n";
        Thread t = new Thread(() -> {
            try {
                send(toEmail, subject, text);
            } catch (Exception ignored) {
                // never let a mail failure affect the saved reply
            }
        }, "forum-reply-email");
        t.setDaemon(true);
        t.start();
    }

    private void sendViaBrevo(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", brevoSenderName, "email", senderEmail()),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text
        );
        rest.post()
                .uri("https://api.brevo.com/v3/smtp/email")
                .header("api-key", brevoApiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}

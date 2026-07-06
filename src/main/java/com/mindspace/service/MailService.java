package com.mindspace.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends plain-text email. Prefers Brevo's HTTPS API (works from hosts that block
 * SMTP, e.g. Render's free tier); falls back to SMTP (JavaMailSender) locally, and
 * finally just logs when nothing is configured.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender; // null when spring.mail.* isn't configured
    private final RestClient rest = RestClient.create();

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

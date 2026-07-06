package com.mindspace.service;

import com.mindspace.model.EmailOtp;
import com.mindspace.repository.EmailOtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Issues and verifies 6-digit email one-time codes for 2-step verification.
 * Codes expire after 10 minutes and allow up to 5 attempts.
 */
@Service
@Transactional
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();

    private final EmailOtpRepository repo;
    private final MailService mailService;

    public OtpService(EmailOtpRepository repo, MailService mailService) {
        this.repo = repo;
        this.mailService = mailService;
    }

    /** Create (or replace) a code for this email+purpose and email it. */
    public void issue(String email, EmailOtp.Purpose purpose, String username, String passwordHash) {
        repo.deleteByEmailAndPurpose(email, purpose);
        String code = String.format("%06d", random.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRY_MINUTES);
        repo.save(new EmailOtp(email, code, purpose, username, passwordHash, expiresAt));
        sendCode(email, code, purpose);
    }

    /** Regenerate a code for an in-progress verification. */
    public void resend(String email, EmailOtp.Purpose purpose) {
        EmailOtp existing = repo.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new IllegalArgumentException("No verification in progress. Please start again."));
        issue(email, purpose, existing.getUsername(), existing.getPasswordHash());
    }

    /**
     * Validate the submitted code. On success the OTP row is consumed (deleted)
     * and returned so the caller can read any stashed registration details.
     *
     * noRollbackFor is essential: a wrong code throws, but we must still persist
     * the incremented attempt counter (and expired/exhausted row deletions).
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public EmailOtp verify(String email, EmailOtp.Purpose purpose, String code) {
        EmailOtp otp = repo.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new IllegalArgumentException("No verification in progress. Please start again."));

        if (otp.isExpired()) {
            repo.delete(otp);
            throw new IllegalArgumentException("Your code has expired. Please request a new one.");
        }
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            repo.delete(otp);
            throw new IllegalArgumentException("Too many incorrect attempts. Please request a new code.");
        }
        if (code == null || !code.trim().equals(otp.getCode())) {
            otp.setAttempts(otp.getAttempts() + 1);
            repo.save(otp);
            throw new IllegalArgumentException("Incorrect code. Please try again.");
        }

        repo.delete(otp);
        return otp;
    }

    private void sendCode(String email, String code, EmailOtp.Purpose purpose) {
        String action;
        if (purpose == EmailOtp.Purpose.REGISTER) action = "create your MindSpace account";
        else if (purpose == EmailOtp.Purpose.RESET) action = "reset your MindSpace password";
        else action = "sign in to MindSpace";
        String subject = "Your MindSpace verification code: " + code;
        String text =
                "Hi,\n\n" +
                "Use this code to " + action + ":\n\n" +
                "        " + code + "\n\n" +
                "The code expires in " + EXPIRY_MINUTES + " minutes. If you didn't request it, you can ignore this email.\n\n" +
                "— MindSpace";
        boolean sent = mailService.send(email, subject, text);
        if (!sent) {
            // Dev fallback: no provider configured — log so testing can continue.
            log.warn("Mail not configured — verification code for {} ({}): {}", email, purpose, code);
        }
    }
}

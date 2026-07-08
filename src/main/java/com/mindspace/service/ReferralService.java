package com.mindspace.service;

import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Invite/referral program. Each user gets an invite code; when a new user signs
 * up with it, both earn AI deep-dive credits.
 */
@Service
@Transactional
public class ReferralService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();
    private static final int REWARD_REFERRER = 5; // credits for inviting a friend
    private static final int REWARD_FRIEND = 2;   // welcome credits for the new user

    private final UserRepository userRepository;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public ReferralService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<String, Object> myInfo(String email) {
        User u = getUser(email);
        String code = ensureCode(u);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("link", frontendUrl + "/signup?ref=" + code);
        out.put("invited", userRepository.countByReferredBy(u.getId()));
        out.put("aiCredits", u.getAiCredits());
        return out;
    }

    /** A new user claims the invite code they signed up with. Idempotent + self-proof. */
    public Map<String, Object> attribute(String email, String code) {
        User u = getUser(email);
        if (u.getReferredBy() == null && code != null && !code.isBlank()) {
            userRepository.findByReferralCode(code.trim().toUpperCase()).ifPresent(referrer -> {
                if (!referrer.getId().equals(u.getId())) {
                    u.setReferredBy(referrer.getId());
                    u.setAiCredits(u.getAiCredits() + REWARD_FRIEND);
                    referrer.setAiCredits(referrer.getAiCredits() + REWARD_REFERRER);
                    userRepository.save(referrer);
                    userRepository.save(u);
                }
            });
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("aiCredits", u.getAiCredits());
        return out;
    }

    /** Spend one AI deep-dive credit. Returns false if the user has none. */
    public boolean spendCredit(String email) {
        User u = getUser(email);
        if (u.getAiCredits() <= 0) return false;
        u.setAiCredits(u.getAiCredits() - 1);
        userRepository.save(u);
        return true;
    }

    public int creditsOf(String email) {
        return getUser(email).getAiCredits();
    }

    private String ensureCode(User u) {
        if (u.getReferralCode() != null && !u.getReferralCode().isBlank()) return u.getReferralCode();
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(ALPHABET[RNG.nextInt(ALPHABET.length)]);
            code = sb.toString();
        } while (userRepository.findByReferralCode(code).isPresent());
        u.setReferralCode(code);
        userRepository.save(u);
        return code;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}

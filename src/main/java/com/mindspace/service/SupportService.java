package com.mindspace.service;

import com.mindspace.dto.SupportDto;
import com.mindspace.model.SupportMessage;
import com.mindspace.model.User;
import com.mindspace.repository.SupportMessageRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class SupportService {

    private final SupportMessageRepository repo;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Value("${app.contact.recipient:kelvinkiumbe589@gmail.com}")
    private String adminRecipient;

    public SupportService(SupportMessageRepository repo, UserRepository userRepository,
                          MailService mailService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.mailService = mailService;
    }

    /**
     * Best-effort email to the admin so they're notified of a new support message.
     * Runs on a background thread so email latency never delays (or blocks) saving
     * the message — the message reaching the admin dashboard is what matters.
     */
    private void notifyAdmin(User user, String text) {
        String body =
                "You have a new support message in MindSpace.\n\n" +
                "From:  " + user.getUsername() + "\n" +
                "Email: " + user.getEmail() + "\n\n" +
                "Message:\n" + text + "\n\n" +
                "Reply from the admin dashboard (Support tab).";
        String subject = "New MindSpace support message from " + user.getUsername();
        Thread t = new Thread(() -> {
            try {
                mailService.send(adminRecipient, subject, body);
            } catch (Exception ignored) {
                // never let a mail failure affect the saved message
            }
        }, "support-notify-admin");
        t.setDaemon(true);
        t.start();
    }

    // Notify the admin of a new guest (landing-page) support message.
    private void notifyAdminGuest(String name, String email, String text) {
        String body =
                "You have a new support message from a website visitor (not logged in).\n\n" +
                "From:  " + name + "\n" +
                "Email: " + email + "\n\n" +
                "Message:\n" + text + "\n\n" +
                "Reply from the admin dashboard (Support tab) — your reply is emailed to the visitor.";
        emailAsync(adminRecipient, "New MindSpace support message from " + name, body);
    }

    // Deliver the admin's reply to a guest by email (they have no in-app inbox).
    private void emailGuestReply(String name, String email, String text) {
        String greeting = (name == null || name.isBlank()) ? "Hi," : "Hi " + name + ",";
        String body =
                greeting + "\n\n" +
                "Thanks for reaching out to MindSpace support. Here's our reply:\n\n" +
                text + "\n\n" +
                "You can reply to this email if you need anything else.\n\n" +
                "— The MindSpace team";
        emailAsync(email, "Re: your MindSpace support request", body);
    }

    // Best-effort email on a daemon thread so mail latency never blocks the request.
    private void emailAsync(String to, String subject, String body) {
        Thread t = new Thread(() -> {
            try {
                mailService.send(to, subject, body);
            } catch (Exception ignored) {
                // never let a mail failure affect the saved message
            }
        }, "support-email");
        t.setDaemon(true);
        t.start();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private SupportDto.MessageResponse toResponse(SupportMessage m) {
        return new SupportDto.MessageResponse(m.getId(), m.getText(), m.isFromAdmin(), m.getCreatedAt());
    }

    public SupportDto.MessageResponse sendUserMessage(String email, String text) {
        User user = getUser(email);
        SupportMessage saved = repo.save(new SupportMessage(user, text, false));
        notifyAdmin(user, text);
        return toResponse(saved);
    }

    // A not-logged-in visitor's message from the landing page. Grouped into a
    // conversation the admin sees alongside logged-in users; replies go by email.
    public SupportDto.MessageResponse sendGuestMessage(String name, String email, String text) {
        String key = guestKeyFor(email);
        SupportMessage saved = repo.save(new SupportMessage(name, email, key, text, false));
        notifyAdminGuest(name, email, text);
        return toResponse(saved);
    }

    // Stable synthetic id (UUID form) per guest email, so their messages group and
    // the admin's existing UUID-keyed thread/reply endpoints work unchanged.
    private String guestKeyFor(String email) {
        String norm = email == null ? "" : email.trim().toLowerCase();
        return UUID.nameUUIDFromBytes(("guest:" + norm).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public List<SupportDto.MessageResponse> myThread(String email) {
        User user = getUser(email);
        return repo.findByUserOrderByCreatedAtAsc(user).stream().map(this::toResponse).toList();
    }

    // ── Admin ──
    public List<SupportDto.Conversation> conversations() {
        List<SupportMessage> all = repo.findAllByOrderByCreatedAtAsc();
        Map<UUID, List<SupportMessage>> byUser = new LinkedHashMap<>();
        Map<String, List<SupportMessage>> byGuest = new LinkedHashMap<>();
        for (SupportMessage m : all) {
            if (m.getUser() != null) {
                byUser.computeIfAbsent(m.getUser().getId(), k -> new ArrayList<>()).add(m);
            } else if (m.getGuestKey() != null) {
                byGuest.computeIfAbsent(m.getGuestKey(), k -> new ArrayList<>()).add(m);
            }
        }
        List<SupportDto.Conversation> convos = new ArrayList<>();
        for (Map.Entry<UUID, List<SupportMessage>> e : byUser.entrySet()) {
            List<SupportMessage> msgs = e.getValue();
            SupportMessage last = msgs.get(msgs.size() - 1);
            User u = last.getUser();
            convos.add(new SupportDto.Conversation(
                    u.getId(), u.getUsername(), u.getEmail(),
                    last.getText(), last.isFromAdmin(), last.getCreatedAt(), msgs.size(), false));
        }
        for (Map.Entry<String, List<SupportMessage>> e : byGuest.entrySet()) {
            List<SupportMessage> msgs = e.getValue();
            SupportMessage last = msgs.get(msgs.size() - 1);
            SupportMessage first = msgs.get(0);
            convos.add(new SupportDto.Conversation(
                    UUID.fromString(e.getKey()), first.getGuestName(), first.getGuestEmail(),
                    last.getText(), last.isFromAdmin(), last.getCreatedAt(), msgs.size(), true));
        }
        convos.sort((a, b) -> b.getLastAt().compareTo(a.getLastAt()));
        return convos;
    }

    public List<SupportDto.MessageResponse> thread(UUID userId) {
        List<SupportMessage> msgs = repo.findByUserIdOrderByCreatedAtAsc(userId);
        if (msgs.isEmpty()) {
            // Fall back to a guest conversation keyed by the synthetic id.
            msgs = repo.findByGuestKeyOrderByCreatedAtAsc(userId.toString());
        }
        return msgs.stream().map(this::toResponse).toList();
    }

    public SupportDto.MessageResponse adminReply(UUID userId, String text) {
        // Logged-in user's conversation → reply appears in their in-app chat.
        var userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            return toResponse(repo.save(new SupportMessage(userOpt.get(), text, true)));
        }
        // Otherwise it's a guest conversation → save the reply and email the visitor.
        List<SupportMessage> guestMsgs = repo.findByGuestKeyOrderByCreatedAtAsc(userId.toString());
        if (guestMsgs.isEmpty()) {
            throw new IllegalArgumentException("Conversation not found");
        }
        SupportMessage ref = guestMsgs.get(0);
        SupportMessage saved = repo.save(new SupportMessage(
                ref.getGuestName(), ref.getGuestEmail(), ref.getGuestKey(), text, true));
        emailGuestReply(ref.getGuestName(), ref.getGuestEmail(), text);
        return toResponse(saved);
    }
}

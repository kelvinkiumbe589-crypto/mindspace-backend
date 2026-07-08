package com.mindspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspace.model.PushSubscription;
import com.mindspace.model.User;
import com.mindspace.repository.PushSubscriptionRepository;
import com.mindspace.repository.UserRepository;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends browser Web Push notifications (VAPID). Dormant until the VAPID keys are
 * configured. Sends run on a background thread so a slow push endpoint never
 * blocks the request that triggered the notification.
 */
@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);

    static { Security.addProvider(new BouncyCastleProvider()); }

    // Public key is safe to embed (it's sent to browsers anyway). The matching
    // private key must be provided via APP_PUSH_VAPID_PRIVATE for sending to work.
    @Value("${app.push.vapid.public:BCMf-4f87vb9tNQwjogXHHMlT1QAyry6EkYOo3jv-6s4uT9hVK4kM8nMvKBlIHmNZBaamGX7w3G6aLb7Kz90DoY}")
    private String vapidPublic;

    @Value("${app.push.vapid.private:}")
    private String vapidPrivate;

    @Value("${app.push.vapid.subject:mailto:kelvinkiumbe589@gmail.com}")
    private String subject;

    private final PushSubscriptionRepository repo;
    private final UserRepository userRepository;
    private final ObjectMapper json = new ObjectMapper();
    private volatile nl.martijndwars.webpush.PushService pushService;

    public WebPushService(PushSubscriptionRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    public boolean isConfigured() {
        return vapidPublic != null && !vapidPublic.isBlank() && vapidPrivate != null && !vapidPrivate.isBlank();
    }

    public String publicKey() { return vapidPublic; }

    /** True if the user has at least one device subscribed to push. */
    public boolean hasSubscription(User user) {
        return user != null && !repo.findByUser(user).isEmpty();
    }

    private nl.martijndwars.webpush.PushService push() throws Exception {
        if (pushService == null) {
            synchronized (this) {
                if (pushService == null) {
                    pushService = new nl.martijndwars.webpush.PushService(vapidPublic, vapidPrivate, subject);
                }
            }
        }
        return pushService;
    }

    @Transactional
    public void subscribe(String email, String endpoint, String p256dh, String auth) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        repo.findByEndpoint(endpoint).ifPresentOrElse(s -> {
            s.setUser(u); s.setP256dh(p256dh); s.setAuth(auth); repo.save(s);
        }, () -> repo.save(new PushSubscription(u, endpoint, p256dh, auth)));
    }

    /** Fire-and-forget push to all of a user's devices. */
    public void sendToUser(User user, String title, String body, String url) {
        if (!isConfigured() || user == null) return;
        List<PushSubscription> subs = repo.findByUser(user);
        if (subs.isEmpty()) return;

        String payload;
        try {
            payload = json.writeValueAsString(Map.of("title", title, "body", body, "url", url == null ? "/" : url));
        } catch (Exception e) {
            return;
        }
        // Snapshot primitives so the background thread doesn't touch detached entities.
        record Sub(UUID id, String endpoint, String p256dh, String auth) {}
        List<Sub> data = subs.stream().map(s -> new Sub(s.getId(), s.getEndpoint(), s.getP256dh(), s.getAuth())).toList();

        Thread t = new Thread(() -> {
            for (Sub s : data) {
                try {
                    Notification n = new Notification(s.endpoint(), s.p256dh(), s.auth(), payload.getBytes(StandardCharsets.UTF_8));
                    var res = push().send(n);
                    int sc = res.getStatusLine().getStatusCode();
                    if (sc == 404 || sc == 410) {         // subscription expired/gone
                        try { repo.deleteById(s.id()); } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    log.warn("Web push failed: {}", e.getMessage());
                }
            }
        }, "web-push");
        t.setDaemon(true);
        t.start();
    }
}

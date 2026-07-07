package com.mindspace.service;

import com.mindspace.model.Booking;
import com.mindspace.model.SessionMessage;
import com.mindspace.model.TherapistProfile;
import com.mindspace.model.User;
import com.mindspace.repository.BookingRepository;
import com.mindspace.repository.TherapistProfileRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authorization + WebRTC config for online (video) sessions. Only the two parties
 * on an APPROVED, ONLINE booking may join its room, and TURN credentials are
 * short-lived HMAC secrets (coturn REST auth) minted per request.
 */
@Service
public class SessionRoomService {

    private final BookingRepository bookingRepo;
    private final UserRepository userRepository;
    private final TherapistProfileRepository profileRepo;
    private final JwtUtil jwtUtil;

    @Value("${app.stun.url:stun:stun.l.google.com:19302}")
    private String stunUrl;

    @Value("${app.turn.host:}")        // e.g. turn.mindspace.example — blank disables TURN
    private String turnHost;

    @Value("${app.turn.secret:}")      // coturn static-auth-secret
    private String turnSecret;

    @Value("${app.turn.ttl:3600}")     // seconds the TURN credential is valid
    private long turnTtl;

    public SessionRoomService(BookingRepository bookingRepo, UserRepository userRepository,
                              TherapistProfileRepository profileRepo, JwtUtil jwtUtil) {
        this.bookingRepo = bookingRepo;
        this.userRepository = userRepository;
        this.profileRepo = profileRepo;
        this.jwtUtil = jwtUtil;
    }

    public record Participant(String bookingId, String role, String counterpartyName) {}

    /** Authorize by email (REST). Throws if not allowed to join this room. */
    public Participant authorize(String email, UUID bookingId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (!"ONLINE".equalsIgnoreCase(b.getSessionType())) {
            throw new IllegalArgumentException("This session isn't an online session.");
        }
        if (b.getStatus() != Booking.Status.APPROVED) {
            throw new IllegalArgumentException("The video room opens once the session is approved.");
        }

        SessionMessage.Role role;
        String counterparty;
        if (b.getClient() != null && b.getClient().getId().equals(user.getId())) {
            role = SessionMessage.Role.CLIENT;
            counterparty = profileRepo.findByUserId(b.getTherapist().getId())
                    .map(TherapistProfile::getName).orElse(b.getTherapist().getUsername());
        } else if (b.getTherapist() != null && b.getTherapist().getId().equals(user.getId())) {
            role = SessionMessage.Role.THERAPIST;
            counterparty = firstName(b.getClient().getUsername());
        } else {
            throw new IllegalArgumentException("This isn't your session.");
        }
        return new Participant(b.getId().toString(), role.name(), counterparty);
    }

    /** Authorize a WebSocket connection by JWT (token comes in the query string). */
    public Participant authorizeToken(String token, UUID bookingId) {
        if (token == null || jwtUtil.isDeviceToken(token) || !jwtUtil.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid token");
        }
        return authorize(jwtUtil.extractEmail(token), bookingId);
    }

    /** ICE servers (STUN always; TURN with time-limited HMAC creds when configured). */
    public Map<String, Object> iceServers() {
        List<Map<String, Object>> servers = new ArrayList<>();
        Map<String, Object> stun = new LinkedHashMap<>();
        stun.put("urls", stunUrl);
        servers.add(stun);

        if (turnHost != null && !turnHost.isBlank() && turnSecret != null && !turnSecret.isBlank()) {
            long expiry = (System.currentTimeMillis() / 1000L) + turnTtl;
            String username = expiry + ":mindspace";
            String credential = hmacSha1Base64(turnSecret, username);
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("urls", List.of(
                    "turn:" + turnHost + ":3478?transport=udp",
                    "turn:" + turnHost + ":3478?transport=tcp",
                    "turns:" + turnHost + ":5349?transport=tcp"));
            turn.put("username", username);
            turn.put("credential", credential);
            servers.add(turn);
        }
        return Map.of("iceServers", servers);
    }

    private String hmacSha1Base64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute TURN credential", e);
        }
    }

    private String firstName(String name) {
        if (name == null || name.isBlank()) return "Client";
        return name.trim().split("\\s+")[0];
    }
}

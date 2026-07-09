package com.mindspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindspace.model.Booking;
import com.mindspace.model.TherapistProfile;
import com.mindspace.model.User;
import com.mindspace.repository.BookingRepository;
import com.mindspace.repository.TherapistProfileRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.ws.ChatWebSocketHandler;
import com.mindspace.ws.SessionSignalingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The "calling" layer over the raw WebRTC signaling. Handles ringing the other
 * party (in-app socket + Web Push), and enforces a connected-time budget per
 * online booking: only time both peers are actually connected is deducted, and
 * the call is force-ended when the budget runs out. Reconnects within the budget
 * are free.
 */
@Service
public class CallSessionService {

    private static final Logger log = LoggerFactory.getLogger(CallSessionService.class);

    private final BookingRepository bookingRepo;
    private final UserRepository userRepository;
    private final TherapistProfileRepository profileRepo;
    private final SessionRoomService roomService;
    private final ChatWebSocketHandler chatHandler;
    private final WebPushService webPushService;
    private final SessionSignalingHandler signalingHandler;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.therapist.portal-url:}")
    private String portalUrl;

    // One force-end timer per live booking, so we can cancel it if the call
    // ends early (either peer hangs up before the budget is spent).
    private final Map<String, ScheduledFuture<?>> pendingEnds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "call-budget");
        t.setDaemon(true);
        return t;
    });

    // @Lazy on the signaling handler breaks the construction cycle (it depends on us).
    public CallSessionService(BookingRepository bookingRepo, UserRepository userRepository,
                              TherapistProfileRepository profileRepo, SessionRoomService roomService,
                              ChatWebSocketHandler chatHandler, WebPushService webPushService,
                              @Lazy SessionSignalingHandler signalingHandler) {
        this.bookingRepo = bookingRepo;
        this.userRepository = userRepository;
        this.profileRepo = profileRepo;
        this.roomService = roomService;
        this.chatHandler = chatHandler;
        this.webPushService = webPushService;
        this.signalingHandler = signalingHandler;
    }

    // ── Budget maths (pure) ──────────────────────────────────────────

    /** Seconds of call time still available, accounting for a segment in progress. */
    public static int remainingSeconds(Booking b) {
        if (b == null) return 0;
        long budget = (long) b.getDurationMinutes() * 60L;
        long used = b.getConsumedSeconds();
        if (b.getCallState() == Booking.CallState.LIVE && b.getSegmentStartedAt() != null) {
            used += Math.max(0, Duration.between(b.getSegmentStartedAt(), LocalDateTime.now()).getSeconds());
        }
        return (int) Math.max(0, budget - used);
    }

    // ── Ringing ──────────────────────────────────────────────────────

    /** Ring the other party for this booking. Returns the current call state. */
    @Transactional
    public Map<String, Object> initiate(String callerEmail, UUID bookingId) {
        // Validates: caller is a party, session is ONLINE and APPROVED.
        roomService.authorize(callerEmail, bookingId);
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (remainingSeconds(b) <= 0) {
            throw new IllegalArgumentException("The paid call time for this session is used up.");
        }

        User caller = userRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + callerEmail));
        boolean callerIsTherapist = b.getTherapist().getId().equals(caller.getId());
        User callee = callerIsTherapist ? b.getClient() : b.getTherapist();

        // Already on the call together — nothing to ring.
        if (b.getCallState() != Booking.CallState.LIVE) {
            b.setCallState(Booking.CallState.RINGING);
            bookingRepo.save(b);
        }

        String callerName = displayName(caller, b);
        String inApp = frame(Map.of(
                "type", "call-invite",
                "bookingId", b.getId().toString(),
                "fromName", callerName,
                "remainingSeconds", remainingSeconds(b)));
        chatHandler.sendToUser(callee.getId(), inApp);

        // Phone/background ring — deep-links straight back into the call.
        String url = deepLink(callee.getId().equals(b.getTherapist().getId()), b.getId());
        webPushService.sendToUser(callee, "Incoming call", callerName + " is calling you", url);

        return state(b, callee);
    }

    /** Callee accepted — tell the caller they're joining. */
    @Transactional
    public Map<String, Object> accept(String email, UUID bookingId) {
        Booking b = party(email, bookingId).booking;
        notifyCounterparty(email, b, Map.of("type", "call-accepted", "bookingId", b.getId().toString()));
        return state(b, null);
    }

    /** Callee declined — tell the caller and go idle. */
    @Transactional
    public Map<String, Object> decline(String email, UUID bookingId) {
        Booking b = party(email, bookingId).booking;
        if (b.getCallState() == Booking.CallState.RINGING) {
            b.setCallState(Booking.CallState.NONE);
            bookingRepo.save(b);
        }
        notifyCounterparty(email, b, Map.of("type", "call-declined", "bookingId", b.getId().toString()));
        return state(b, null);
    }

    /** Caller gave up ringing before it was answered. */
    @Transactional
    public Map<String, Object> cancel(String email, UUID bookingId) {
        Booking b = party(email, bookingId).booking;
        if (b.getCallState() == Booking.CallState.RINGING) {
            b.setCallState(Booking.CallState.NONE);
            bookingRepo.save(b);
        }
        notifyCounterparty(email, b, Map.of("type", "call-canceled", "bookingId", b.getId().toString()));
        return state(b, null);
    }

    /** Current call state for either party (call state, remaining time, is the peer live). */
    @Transactional(readOnly = true)
    public Map<String, Object> state(String email, UUID bookingId) {
        Party p = party(email, bookingId);
        return state(p.booking, p.counterparty);
    }

    // ── Connected-time clock (driven by the signaling handler) ───────

    /** Both peers are now connected — start charging time and arm the force-end. */
    @Transactional
    public void onBothConnected(String bookingId) {
        Booking b = load(bookingId);
        if (b == null) return;
        if (b.getSegmentStartedAt() == null) {
            b.setSegmentStartedAt(LocalDateTime.now());
        }
        b.setCallState(Booking.CallState.LIVE);
        bookingRepo.save(b);

        int remaining = remainingSeconds(b);
        armForceEnd(bookingId, remaining);
    }

    /** A peer left (room no longer full) — bank the elapsed time and disarm. */
    @Transactional
    public void onRoomDrain(String bookingId) {
        cancelForceEnd(bookingId);
        Booking b = load(bookingId);
        if (b == null) return;
        if (b.getSegmentStartedAt() != null) {
            long secs = Math.max(0, Duration.between(b.getSegmentStartedAt(), LocalDateTime.now()).getSeconds());
            b.setConsumedSeconds((int) Math.min(Integer.MAX_VALUE, b.getConsumedSeconds() + secs));
            b.setSegmentStartedAt(null);
        }
        // Idle again (can rejoin/re-call) unless the budget is spent.
        b.setCallState(remainingSeconds(b) <= 0 ? Booking.CallState.ENDED : Booking.CallState.NONE);
        bookingRepo.save(b);
    }

    private void armForceEnd(String bookingId, int seconds) {
        cancelForceEnd(bookingId);
        ScheduledFuture<?> f = scheduler.schedule(() -> {
            try {
                // Closing the sockets triggers onRoomDrain, which banks the time
                // and flips the state to ENDED.
                signalingHandler.closeRoom(bookingId, "time-up");
            } catch (Exception e) {
                log.warn("force-end failed for {}: {}", bookingId, e.getMessage());
            }
        }, Math.max(1, seconds), TimeUnit.SECONDS);
        pendingEnds.put(bookingId, f);
    }

    private void cancelForceEnd(String bookingId) {
        ScheduledFuture<?> f = pendingEnds.remove(bookingId);
        if (f != null) f.cancel(false);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private record Party(Booking booking, User self, User counterparty) {}

    private Party party(String email, UUID bookingId) {
        roomService.authorize(email, bookingId); // throws unless a party on an ONLINE/APPROVED booking
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        User self = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        User counterparty = b.getTherapist().getId().equals(self.getId()) ? b.getClient() : b.getTherapist();
        return new Party(b, self, counterparty);
    }

    private Booking load(String bookingId) {
        try {
            return bookingRepo.findById(UUID.fromString(bookingId)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void notifyCounterparty(String actingEmail, Booking b, Map<String, Object> payload) {
        User acting = userRepository.findByEmail(actingEmail).orElse(null);
        if (acting == null) return;
        User other = b.getTherapist().getId().equals(acting.getId()) ? b.getClient() : b.getTherapist();
        chatHandler.sendToUser(other.getId(), frame(payload));
    }

    private Map<String, Object> state(Booking b, User counterparty) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bookingId", b.getId().toString());
        out.put("callState", b.getCallState().name());
        out.put("remainingSeconds", remainingSeconds(b));
        out.put("durationMinutes", b.getDurationMinutes());
        if (counterparty != null) out.put("peerOnline", chatHandler.isOnline(counterparty.getId()));
        return out;
    }

    // The caller's name as the callee should see it (therapist's profile name, or
    // the client's first name — matching the privacy rules used elsewhere).
    private String displayName(User caller, Booking b) {
        if (b.getTherapist().getId().equals(caller.getId())) {
            return profileRepo.findByUserId(caller.getId())
                    .map(TherapistProfile::getName).orElse(caller.getUsername());
        }
        String name = caller.getUsername();
        if (name == null || name.isBlank()) return "Your client";
        return name.trim().split("\\s+")[0];
    }

    private String deepLink(boolean calleeIsTherapist, UUID bookingId) {
        String base = calleeIsTherapist && portalUrl != null && !portalUrl.isBlank() ? portalUrl : frontendUrl;
        if (base == null || base.isBlank()) base = "/";
        String sep = base.endsWith("/") ? "" : "/";
        return base + sep + "?call=" + bookingId;
    }

    private String frame(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"type\":\"noop\"}";
        }
    }
}

package com.mindspace.service;

import com.mindspace.dto.BookingDto;
import com.mindspace.model.Booking;
import com.mindspace.model.TherapistProfile;
import com.mindspace.model.User;
import com.mindspace.repository.BookingRepository;
import com.mindspace.repository.TherapistProfileRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class BookingService {

    private final BookingRepository bookingRepo;
    private final TherapistProfileRepository profileRepo;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final WhatsAppService whatsAppService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @org.springframework.beans.factory.annotation.Value("${app.therapist.portal-url:}")
    private String portalUrl;

    @org.springframework.beans.factory.annotation.Value("${app.whatsapp.template.session-confirmed:session_confirmed}")
    private String waTemplateConfirmed;

    public BookingService(BookingRepository bookingRepo, TherapistProfileRepository profileRepo,
                          UserRepository userRepository, MailService mailService,
                          WhatsAppService whatsAppService) {
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.whatsAppService = whatsAppService;
    }

    private User user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // ── Client ──

    public BookingDto.Response create(String clientEmail, BookingDto.CreateRequest req) {
        User client = user(clientEmail);
        UUID therapistUserId;
        try {
            therapistUserId = UUID.fromString(req.getTherapistId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid therapist");
        }
        User therapist = userRepository.findById(therapistUserId)
                .orElseThrow(() -> new IllegalArgumentException("Therapist not found"));
        TherapistProfile profile = profileRepo.findByUserId(therapistUserId)
                .orElseThrow(() -> new IllegalArgumentException("Therapist not found"));

        boolean physical = "PHYSICAL".equalsIgnoreCase(req.getSessionType());

        // Online sessions are billed by the call time the client picks (default 60m,
        // in whole hours). amount = online hourly rate * hours. Physical is a flat visit.
        int durationMinutes = normalizeDuration(req.getDurationMinutes());
        int amount = physical
                ? TherapistService.effectivePhysicalPrice(profile)
                : (int) Math.round(profile.getPriceOnline() * (durationMinutes / 60.0));

        Booking b = new Booking();
        b.setClient(client);
        b.setTherapist(therapist);
        b.setSessionType(physical ? "PHYSICAL" : "ONLINE");
        b.setAmount(amount);
        b.setDurationMinutes(physical ? 60 : durationMinutes);
        b.setScheduledAt(parseWhen(req.getScheduledAt()));
        b.setClientPhone(req.getPhone());
        b.setStatus(Booking.Status.PENDING_PAYMENT);
        return toResponse(bookingRepo.save(b));
    }

    public BookingDto.Response markPaid(String clientEmail, UUID bookingId, String orderTrackingId) {
        Booking b = ownedByClient(bookingId, clientEmail);
        boolean wasPending = b.getStatus() == Booking.Status.PENDING_PAYMENT;
        b.setStatus(Booking.Status.AWAITING_APPROVAL);
        b.setOrderTrackingId(orderTrackingId);
        Booking saved = bookingRepo.save(b);
        if (wasPending) notifyTherapistNewBooking(saved);
        return toResponse(saved);
    }

    /**
     * Record the Pesapal tracking id on the booking as soon as checkout starts,
     * BEFORE the user pays. This lets the server-side IPN correlate the payment
     * back to this booking even if the client's browser is closed mid-payment.
     * Status stays PENDING_PAYMENT until the payment is confirmed.
     */
    public BookingDto.Response attachOrder(String clientEmail, UUID bookingId, String orderTrackingId) {
        Booking b = ownedByClient(bookingId, clientEmail);
        b.setOrderTrackingId(orderTrackingId);
        return toResponse(bookingRepo.save(b));
    }

    /**
     * Confirm a booking as paid from a trusted server-side signal (Pesapal IPN,
     * already verified against GetTransactionStatus). Idempotent: only a booking
     * still awaiting payment is advanced, so re-delivered IPNs are harmless.
     */
    public void confirmPaidByTrackingId(String orderTrackingId) {
        if (orderTrackingId == null || orderTrackingId.isBlank()) return;
        bookingRepo.findByOrderTrackingId(orderTrackingId).ifPresent(b -> {
            if (b.getStatus() == Booking.Status.PENDING_PAYMENT) {
                b.setStatus(Booking.Status.AWAITING_APPROVAL);
                notifyTherapistNewBooking(bookingRepo.save(b));
            }
        });
    }

    public BookingDto.Response markFailed(String clientEmail, UUID bookingId) {
        Booking b = ownedByClient(bookingId, clientEmail);
        b.setStatus(Booking.Status.FAILED);
        return toResponse(bookingRepo.save(b));
    }

    public java.util.List<BookingDto.Response> myBookings(String clientEmail) {
        return bookingRepo.findByClientOrderByCreatedAtDesc(user(clientEmail)).stream().map(this::toResponse).toList();
    }

    /** Client removes an incomplete booking (payment never completed). */
    public void delete(String clientEmail, UUID bookingId) {
        Booking b = ownedByClient(bookingId, clientEmail);
        if (b.getStatus() != Booking.Status.PENDING_PAYMENT && b.getStatus() != Booking.Status.FAILED) {
            throw new IllegalArgumentException("Only incomplete bookings can be removed");
        }
        bookingRepo.delete(b);
    }

    /** Client rates a completed session; the therapist's average rating is recomputed. */
    public BookingDto.Response rate(String clientEmail, UUID bookingId, int rating) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be between 1 and 5");
        Booking b = ownedByClient(bookingId, clientEmail);
        if (b.getStatus() != Booking.Status.DONE) {
            throw new IllegalArgumentException("You can only rate completed sessions");
        }
        b.setRating(rating);
        bookingRepo.save(b);
        recomputeRating(b.getTherapist());
        return toResponse(b);
    }

    private void recomputeRating(User therapist) {
        java.util.List<Booking> rated = bookingRepo.findByTherapistOrderByScheduledAtAsc(therapist).stream()
                .filter(x -> x.getRating() != null).toList();
        if (rated.isEmpty()) return;
        double avg = rated.stream().mapToInt(Booking::getRating).average().orElse(5.0);
        profileRepo.findByUserId(therapist.getId()).ifPresent(p -> {
            p.setRating(Math.round(avg * 10) / 10.0);
            p.setReviews(rated.size());
            profileRepo.save(p);
        });
    }

    // ── Therapist ──

    public java.util.List<BookingDto.Response> therapistBookings(String therapistEmail) {
        // only paid+ bookings are worth showing the therapist
        return bookingRepo.findByTherapistOrderByScheduledAtAsc(user(therapistEmail)).stream()
                .filter(b -> b.getStatus() != Booking.Status.PENDING_PAYMENT && b.getStatus() != Booking.Status.FAILED)
                .map(this::toResponse).toList();
    }

    public BookingDto.Response approve(String therapistEmail, UUID bookingId) {
        Booking b = ownedByTherapist(bookingId, therapistEmail);
        b.setStatus(Booking.Status.APPROVED);
        // In-person sessions get a check-in code the client shows on arrival.
        if ("PHYSICAL".equalsIgnoreCase(b.getSessionType()) && (b.getCheckInCode() == null || b.getCheckInCode().isBlank())) {
            b.setCheckInCode(generateCode());
        }
        Booking saved = bookingRepo.save(b);
        notifyClientApproved(saved);
        // WhatsApp confirmation to the client (if configured + we have their number).
        whatsAppService.sendTemplateAsync(saved.getClientPhone(), waTemplateConfirmed,
                firstName(saved.getClient().getUsername()), therapistName(saved), whenLabel(saved));
        return toResponse(saved);
    }

    public BookingDto.Response markDone(String therapistEmail, UUID bookingId) {
        Booking b = ownedByTherapist(bookingId, therapistEmail);
        b.setStatus(Booking.Status.DONE);
        return toResponse(bookingRepo.save(b));
    }

    /** Therapist verifies the code the client shows at an in-person session. */
    public BookingDto.Response checkIn(String therapistEmail, UUID bookingId, String code) {
        Booking b = ownedByTherapist(bookingId, therapistEmail);
        if (!"PHYSICAL".equalsIgnoreCase(b.getSessionType())) {
            throw new IllegalArgumentException("This isn't an in-person session.");
        }
        if (b.getCheckInCode() == null || code == null || !b.getCheckInCode().equalsIgnoreCase(code.trim())) {
            throw new IllegalArgumentException("That check-in code doesn't match.");
        }
        b.setCheckedIn(true);
        return toResponse(bookingRepo.save(b));
    }

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(CODE_ALPHABET[RNG.nextInt(CODE_ALPHABET.length)]);
        return sb.toString();
    }

    // ── helpers ──

    private Booking ownedByClient(UUID bookingId, String clientEmail) {
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (!b.getClient().getEmail().equalsIgnoreCase(clientEmail)) {
            throw new IllegalArgumentException("Not your booking");
        }
        return b;
    }

    private Booking ownedByTherapist(UUID bookingId, String therapistEmail) {
        Booking b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (!b.getTherapist().getEmail().equalsIgnoreCase(therapistEmail)) {
            throw new IllegalArgumentException("Not your booking");
        }
        return b;
    }

    private String firstName(String name) {
        if (name == null || name.isBlank()) return "Client";
        return name.trim().split("\\s+")[0];
    }

    // ── Lifecycle emails ──────────────────────────────────────────

    private String therapistName(Booking b) {
        return profileRepo.findByUserId(b.getTherapist().getId())
                .map(TherapistProfile::getName).orElse(b.getTherapist().getUsername());
    }

    private String whenLabel(Booking b) {
        return b.getScheduledAt() == null ? "a time to be confirmed" : b.getScheduledAt().toString().replace("T", " ");
    }

    // Therapist is told a new session was booked & paid — no client contact shared.
    private void notifyTherapistNewBooking(Booking b) {
        if (b.getTherapist() == null || b.getTherapist().getEmail() == null) return;
        String kind = "PHYSICAL".equalsIgnoreCase(b.getSessionType()) ? "in-person" : "online";
        String where = portalUrl == null || portalUrl.isBlank() ? "your MindSpace therapist portal" : portalUrl;
        String body =
                "Hi " + therapistName(b) + ",\n\n" +
                "You have a new " + kind + " session request from a client (" + firstName(b.getClient().getUsername()) + ") for " + whenLabel(b) + ".\n\n" +
                "Approve it in " + where + " — once you approve, the client is notified and you can message each other in the app.\n\n" +
                "— MindSpace";
        emailAsync(b.getTherapist().getEmail(), "New session request on MindSpace", body);
    }

    // Client is told their session is confirmed, with join/location + a calendar link.
    private void notifyClientApproved(Booking b) {
        if (b.getClient() == null || b.getClient().getEmail() == null) return;
        boolean physical = "PHYSICAL".equalsIgnoreCase(b.getSessionType());
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(firstName(b.getClient().getUsername())).append(",\n\n")
            .append("Good news — ").append(therapistName(b)).append(" approved your session for ").append(whenLabel(b)).append(".\n\n");
        String location;
        if (physical) {
            TherapistProfile p = profileRepo.findByUserId(b.getTherapist().getId()).orElse(null);
            String addr = p == null ? null : p.getPracticeAddress();
            location = addr == null ? "In person" : addr;
            body.append("It's an in-person session.\n");
            if (addr != null) body.append("Location: ").append(addr).append("\n");
            if (p != null && mapUrl(p) != null) body.append("Map: ").append(mapUrl(p)).append("\n");
            if (b.getCheckInCode() != null) body.append("Your check-in code: ").append(b.getCheckInCode()).append("\n");
        } else {
            location = "Online (MindSpace)";
            body.append("It's an online video session. Join from Find a Therapist → Upcoming: ")
                .append(frontendUrl).append("/find-a-therapist\n");
        }
        String cal = googleCalendarLink("MindSpace session with " + therapistName(b), b.getScheduledAt(), location);
        if (cal != null) body.append("\nAdd to Google Calendar: ").append(cal).append("\n");
        body.append("\n— MindSpace");
        emailAsync(b.getClient().getEmail(), "Your MindSpace session is confirmed", body.toString());
    }

    // A one-click "Add to Google Calendar" link for a 1-hour session.
    private String googleCalendarLink(String title, LocalDateTime start, String location) {
        if (start == null) return null;
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String dates = start.format(fmt) + "/" + start.plusHours(1).format(fmt);
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + "&dates=" + dates
                + "&location=" + URLEncoder.encode(location == null ? "" : location, StandardCharsets.UTF_8)
                + "&details=" + URLEncoder.encode("Your MindSpace therapy session.", StandardCharsets.UTF_8);
    }

    private void emailAsync(String to, String subject, String body) {
        Thread t = new Thread(() -> {
            try { mailService.send(to, subject, body); } catch (Exception ignored) {}
        }, "booking-email");
        t.setDaemon(true);
        t.start();
    }

    // Call time the client picks, in 30-minute steps, clamped to 30 min .. 4 hours.
    // Defaults to 60 minutes when unspecified.
    private int normalizeDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) return 60;
        int m = Math.round(minutes / 30.0f) * 30;
        return Math.max(30, Math.min(240, m));
    }

    private LocalDateTime parseWhen(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            String s = iso.length() > 16 ? iso.substring(0, 16) : iso; // trim seconds/zone
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private BookingDto.Response toResponse(Booking b) {
        BookingDto.Response r = new BookingDto.Response();
        r.id = b.getId().toString();
        r.therapistId = b.getTherapist().getId().toString();
        r.therapistName = profileRepo.findByUserId(b.getTherapist().getId())
                .map(TherapistProfile::getName).orElse(b.getTherapist().getUsername());
        // Privacy: the therapist sees the client by first name only, never their email.
        r.clientName = firstName(b.getClient().getUsername());
        r.clientEmail = null;
        r.sessionType = b.getSessionType();
        r.amount = b.getAmount();
        r.durationMinutes = b.getDurationMinutes();
        r.remainingSeconds = CallSessionService.remainingSeconds(b);
        r.callState = b.getCallState().name();
        // scheduledAt is the user-picked wall-clock time — keep it naive (no zone).
        r.scheduledAt = b.getScheduledAt() == null ? null : b.getScheduledAt().toString();
        r.status = b.getStatus().name();
        // createdAt is a server-generated UTC instant — mark it as UTC so the browser
        // converts it to the viewer's local time correctly.
        r.createdAt = b.getCreatedAt() == null ? null : b.getCreatedAt().atOffset(java.time.ZoneOffset.UTC).toString();
        r.rating = b.getRating();
        r.checkedIn = b.isCheckedIn();

        // Reveal in-person location + check-in code only once the booking is approved.
        boolean physical = "PHYSICAL".equalsIgnoreCase(b.getSessionType());
        boolean revealed = b.getStatus() == Booking.Status.APPROVED || b.getStatus() == Booking.Status.DONE;
        if (physical && revealed) {
            profileRepo.findByUserId(b.getTherapist().getId()).ifPresent(p -> {
                r.practiceAddress = p.getPracticeAddress();
                r.practiceNotes = p.getPracticeNotes();
                r.practiceMapUrl = mapUrl(p);
            });
            r.checkInCode = b.getCheckInCode();
        }
        return r;
    }

    // A maps link the client can tap: the therapist's own link if set, else a
    // Google Maps search for the practice address.
    private String mapUrl(TherapistProfile p) {
        if (p.getPracticeMapUrl() != null && !p.getPracticeMapUrl().isBlank()) return p.getPracticeMapUrl();
        if (p.getPracticeAddress() != null && !p.getPracticeAddress().isBlank()) {
            return "https://www.google.com/maps/search/?api=1&query="
                    + URLEncoder.encode(p.getPracticeAddress(), StandardCharsets.UTF_8);
        }
        return null;
    }
}

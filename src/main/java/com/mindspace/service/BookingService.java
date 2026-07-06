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

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class BookingService {

    private final BookingRepository bookingRepo;
    private final TherapistProfileRepository profileRepo;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepo, TherapistProfileRepository profileRepo,
                          UserRepository userRepository) {
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.userRepository = userRepository;
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
        int amount = physical ? (int) Math.round(profile.getPriceOnline() * 1.5) : profile.getPriceOnline();

        Booking b = new Booking();
        b.setClient(client);
        b.setTherapist(therapist);
        b.setSessionType(physical ? "PHYSICAL" : "ONLINE");
        b.setAmount(amount);
        b.setScheduledAt(parseWhen(req.getScheduledAt()));
        b.setStatus(Booking.Status.PENDING_PAYMENT);
        return toResponse(bookingRepo.save(b));
    }

    public BookingDto.Response markPaid(String clientEmail, UUID bookingId, String orderTrackingId) {
        Booking b = ownedByClient(bookingId, clientEmail);
        b.setStatus(Booking.Status.AWAITING_APPROVAL);
        b.setOrderTrackingId(orderTrackingId);
        return toResponse(bookingRepo.save(b));
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
                bookingRepo.save(b);
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
        return toResponse(bookingRepo.save(b));
    }

    public BookingDto.Response markDone(String therapistEmail, UUID bookingId) {
        Booking b = ownedByTherapist(bookingId, therapistEmail);
        b.setStatus(Booking.Status.DONE);
        return toResponse(bookingRepo.save(b));
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
        r.clientName = b.getClient().getUsername();
        r.clientEmail = b.getClient().getEmail();
        r.sessionType = b.getSessionType();
        r.amount = b.getAmount();
        // scheduledAt is the user-picked wall-clock time — keep it naive (no zone).
        r.scheduledAt = b.getScheduledAt() == null ? null : b.getScheduledAt().toString();
        r.status = b.getStatus().name();
        // createdAt is a server-generated UTC instant — mark it as UTC so the browser
        // converts it to the viewer's local time correctly.
        r.createdAt = b.getCreatedAt() == null ? null : b.getCreatedAt().atOffset(java.time.ZoneOffset.UTC).toString();
        r.rating = b.getRating();
        return r;
    }
}

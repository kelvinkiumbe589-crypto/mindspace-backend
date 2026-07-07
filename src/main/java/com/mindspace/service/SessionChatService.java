package com.mindspace.service;

import com.mindspace.dto.SessionDto;
import com.mindspace.model.Booking;
import com.mindspace.model.SessionMessage;
import com.mindspace.model.TherapistProfile;
import com.mindspace.model.User;
import com.mindspace.repository.BookingRepository;
import com.mindspace.repository.SessionMessageRepository;
import com.mindspace.repository.TherapistProfileRepository;
import com.mindspace.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Private per-booking chat between a client and their therapist. Enforces that the
 * caller is a party on the booking and that the session is far enough along, and
 * never exposes either party's personal contact — only a display name and role.
 */
@Service
@Transactional
public class SessionChatService {

    private final SessionMessageRepository repo;
    private final BookingRepository bookingRepo;
    private final UserRepository userRepository;
    private final TherapistProfileRepository profileRepo;
    private final NotificationService notificationService;

    public SessionChatService(SessionMessageRepository repo, BookingRepository bookingRepo,
                              UserRepository userRepository, TherapistProfileRepository profileRepo,
                              NotificationService notificationService) {
        this.repo = repo;
        this.bookingRepo = bookingRepo;
        this.userRepository = userRepository;
        this.profileRepo = profileRepo;
        this.notificationService = notificationService;
    }

    public SessionDto.Thread thread(String email, UUID bookingId) {
        User caller = getUser(email);
        Booking b = getBooking(bookingId);
        SessionMessage.Role role = roleOf(caller, b);
        boolean canChat = canChat(b);
        List<SessionDto.MessageResponse> msgs = repo.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(m -> toResponse(m, role))
                .toList();
        return new SessionDto.Thread(
                b.getId().toString(), counterpartyName(b, role), b.getSessionType(),
                b.getStatus().name(), canChat, msgs);
    }

    public SessionDto.MessageResponse send(String email, UUID bookingId, String text) {
        User caller = getUser(email);
        Booking b = getBooking(bookingId);
        SessionMessage.Role role = roleOf(caller, b);
        if (!canChat(b)) {
            throw new IllegalArgumentException("Chat opens once the session is approved.");
        }
        SessionMessage saved = repo.save(new SessionMessage(b, role, text));
        notifyCounterparty(b, role);
        return toResponse(saved, role);
    }

    // ── helpers ──

    // Chat is available once approved and stays open through completion (follow-ups).
    private boolean canChat(Booking b) {
        return b.getStatus() == Booking.Status.APPROVED || b.getStatus() == Booking.Status.DONE;
    }

    private SessionMessage.Role roleOf(User caller, Booking b) {
        if (b.getClient() != null && b.getClient().getId().equals(caller.getId())) {
            return SessionMessage.Role.CLIENT;
        }
        if (b.getTherapist() != null && b.getTherapist().getId().equals(caller.getId())) {
            return SessionMessage.Role.THERAPIST;
        }
        throw new IllegalArgumentException("This isn't your session.");
    }

    // The counterparty as the caller should see them — name only, never contact.
    private String counterpartyName(Booking b, SessionMessage.Role callerRole) {
        if (callerRole == SessionMessage.Role.CLIENT) {
            return profileRepo.findByUserId(b.getTherapist().getId())
                    .map(TherapistProfile::getName)
                    .orElse(b.getTherapist().getUsername());
        }
        // Therapist sees the client by first name only.
        return firstName(b.getClient().getUsername());
    }

    private void notifyCounterparty(Booking b, SessionMessage.Role senderRole) {
        if (senderRole == SessionMessage.Role.THERAPIST) {
            // Client uses the main app (with the notification bell) — let them know.
            notificationService.create(b.getClient(), "SESSION_MESSAGE",
                    "New message from your therapist about your session", "/bookings");
        } else {
            notificationService.create(b.getTherapist(), "SESSION_MESSAGE",
                    "New message from " + firstName(b.getClient().getUsername()) + " about a session", "/bookings");
        }
    }

    private String firstName(String name) {
        if (name == null || name.isBlank()) return "Client";
        return name.trim().split("\\s+")[0];
    }

    private SessionDto.MessageResponse toResponse(SessionMessage m, SessionMessage.Role callerRole) {
        return new SessionDto.MessageResponse(
                m.getId(), m.getText(), m.getSenderRole().name(),
                m.getSenderRole() == callerRole, m.getCreatedAt());
    }

    private Booking getBooking(UUID id) {
        return bookingRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}

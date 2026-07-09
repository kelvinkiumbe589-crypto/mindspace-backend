package com.mindspace.controller;

import com.mindspace.dto.TherapistDto;
import com.mindspace.dto.TipDto;
import com.mindspace.dto.WalletDto;
import com.mindspace.model.Booking;
import com.mindspace.model.User;
import com.mindspace.repository.BookingRepository;
import com.mindspace.repository.MoodEntryRepository;
import com.mindspace.repository.SupportMessageRepository;
import com.mindspace.repository.UserRepository;
import com.mindspace.dto.RatingDto;
import com.mindspace.service.AnalyticsService;
import com.mindspace.service.RatingService;
import com.mindspace.service.TherapistService;
import com.mindspace.service.TipService;
import com.mindspace.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final MoodEntryRepository moodEntryRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final TherapistService therapistService;
    private final BookingRepository bookingRepository;
    private final WalletService walletService;
    private final TipService tipService;
    private final RatingService ratingService;
    private final AnalyticsService analyticsService;

    
    public AdminController(UserRepository userRepository, MoodEntryRepository moodEntryRepository,
                           SupportMessageRepository supportMessageRepository,
                           TherapistService therapistService, BookingRepository bookingRepository,
                           WalletService walletService, TipService tipService,
                           RatingService ratingService, AnalyticsService analyticsService) {
        this.userRepository = userRepository;
        this.moodEntryRepository = moodEntryRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.therapistService = therapistService;
        this.bookingRepository = bookingRepository;
        this.walletService = walletService;
        this.tipService = tipService;
        this.ratingService = ratingService;
        this.analyticsService = analyticsService;
    }

    // ── Visitor / page-view analytics ──
    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        return analyticsService.summary();
    }

    // ── App ratings ──
    @GetMapping("/ratings")
    public List<RatingDto.Response> ratings() {
        return ratingService.all();
    }

    @GetMapping("/ratings/summary")
    public RatingDto.Summary ratingsSummary() {
        return ratingService.summary();
    }

    // ── Payouts (withdrawals) ──
    @GetMapping("/withdrawals")
    public List<WalletDto.WithdrawalResponse> withdrawals() {
        return walletService.allWithdrawals();
    }

    @PostMapping("/withdrawals/{id}/paid")
    public WalletDto.WithdrawalResponse markWithdrawalPaid(@PathVariable UUID id) {
        return walletService.markPaid(id);
    }

    @PostMapping("/withdrawals/{id}/reject")
    public WalletDto.WithdrawalResponse rejectWithdrawal(@PathVariable UUID id) {
        return walletService.reject(id);
    }

    // ── Tips ──
    @GetMapping("/tips")
    public List<TipDto.Response> tips() {
        return tipService.list();
    }

    // ── Users ──
    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        List<User> users = new ArrayList<>(userRepository.findAll());
        users.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt()); // newest first
        });
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId().toString());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("role", u.getRole().name());
            m.put("createdAt", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
            out.add(m);
        }
        return out;
    }

    // ── Bookings / activity log ──
    @GetMapping("/bookings")
    public List<Map<String, Object>> bookings() {
        List<Booking> all = new ArrayList<>(bookingRepository.findAll());
        all.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt()); // newest first
        });
        List<Map<String, Object>> out = new ArrayList<>();
        for (Booking b : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId().toString());
            m.put("client", b.getClient() == null ? null : b.getClient().getUsername());
            m.put("clientEmail", b.getClient() == null ? null : b.getClient().getEmail());
            m.put("therapist", b.getTherapist() == null ? null : b.getTherapist().getUsername());
            m.put("sessionType", b.getSessionType());
            m.put("amount", b.getAmount());
            m.put("status", b.getStatus().name());
            m.put("checkedIn", b.isCheckedIn());
            m.put("scheduledAt", b.getScheduledAt() == null ? null : b.getScheduledAt().toString());
            m.put("createdAt", b.getCreatedAt() == null ? null : b.getCreatedAt().atOffset(java.time.ZoneOffset.UTC).toString());
            out.add(m);
        }
        return out;
    }

    // ── Therapist management ──
    @GetMapping("/therapists")
    public List<TherapistDto.Response> listTherapists() {
        return therapistService.list();
    }

    @PostMapping("/therapists")
    public TherapistDto.Response createTherapist(@Valid @RequestBody TherapistDto.CreateRequest req) {
        return therapistService.create(req);
    }

    @PutMapping("/therapists/{id}")
    public TherapistDto.Response updateTherapist(@PathVariable UUID id,
                                                 @Valid @RequestBody TherapistDto.UpdateRequest req) {
        return therapistService.update(id, req);
    }

    @DeleteMapping("/therapists/{id}")
    public Map<String, Object> deleteTherapist(@PathVariable UUID id) {
        therapistService.delete(id);
        return Map.of("deleted", true);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LocalDateTime now = LocalDateTime.now();

        long totalUsers = userRepository.count();
        long newUsers7d = userRepository.countByCreatedAtAfter(now.minusDays(7));
        long newUsers30d = userRepository.countByCreatedAtAfter(now.minusDays(30));
        long totalMoods = moodEntryRepository.count();
        // Count distinct conversations across both logged-in users and guests.
        long conversations = supportMessageRepository.findAll().stream()
                .map(m -> m.getUser() != null ? m.getUser().getId().toString() : m.getGuestKey())
                .filter(k -> k != null)
                .distinct().count();

        // Paid transactions = bookings that got past payment
        List<Booking> bookings = bookingRepository.findAll();
        long transactions = bookings.stream()
                .filter(b -> b.getStatus() != Booking.Status.PENDING_PAYMENT && b.getStatus() != Booking.Status.FAILED)
                .count();
        long revenue = bookings.stream()
                .filter(b -> b.getStatus() != Booking.Status.PENDING_PAYMENT && b.getStatus() != Booking.Status.FAILED)
                .mapToLong(Booking::getAmount).sum();
        long completedSessions = bookings.stream().filter(b -> b.getStatus() == Booking.Status.DONE).count();

        // Signups per day for the last 7 days
        List<User> users = userRepository.findAll();
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) counts.put(LocalDate.now().minusDays(i), 0);
        for (User u : users) {
            if (u.getCreatedAt() == null) continue;
            LocalDate d = u.getCreatedAt().toLocalDate();
            if (counts.containsKey(d)) counts.merge(d, 1, Integer::sum);
        }
        List<Map<String, Object>> signupsByDay = new ArrayList<>();
        counts.forEach((day, count) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("count", count);
            signupsByDay.add(m);
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalUsers", totalUsers);
        out.put("newUsers7d", newUsers7d);
        out.put("newUsers30d", newUsers30d);
        out.put("totalMoods", totalMoods);
        out.put("conversations", conversations);
        out.put("transactions", transactions);
        out.put("revenue", revenue);
        out.put("completedSessions", completedSessions);
        out.put("tips", tipService.countPaid());
        out.put("tipsTotal", tipService.totalPaid());
        out.put("signupsByDay", signupsByDay);
        return out;
    }
}

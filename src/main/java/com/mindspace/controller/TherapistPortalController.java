package com.mindspace.controller;

import com.mindspace.dto.BookingDto;
import com.mindspace.dto.TherapistDto;
import com.mindspace.dto.WalletDto;
import com.mindspace.service.BookingService;
import com.mindspace.service.TherapistService;
import com.mindspace.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Therapist-portal endpoints — sessions booked with the logged-in therapist. */
@RestController
@RequestMapping("/api/therapist")
@PreAuthorize("hasRole('THERAPIST')")
public class TherapistPortalController {

    private final BookingService bookingService;
    private final TherapistService therapistService;
    private final WalletService walletService;

    public TherapistPortalController(BookingService bookingService, TherapistService therapistService,
                                     WalletService walletService) {
        this.bookingService = bookingService;
        this.therapistService = therapistService;
        this.walletService = walletService;
    }

    // ── Earnings + withdrawals ──
    @GetMapping("/earnings")
    public WalletDto.Earnings earnings(@AuthenticationPrincipal UserDetails therapist) {
        return walletService.earnings(therapist.getUsername());
    }

    @PostMapping("/withdrawals")
    public WalletDto.WithdrawalResponse requestWithdrawal(@AuthenticationPrincipal UserDetails therapist,
                                                          @RequestBody WalletDto.WithdrawalRequest req) {
        return walletService.requestWithdrawal(therapist.getUsername(), req);
    }

    @GetMapping("/withdrawals")
    public List<WalletDto.WithdrawalResponse> withdrawals(@AuthenticationPrincipal UserDetails therapist) {
        return walletService.myWithdrawals(therapist.getUsername());
    }

    // ── Own profile: price + availability ──
    @GetMapping("/profile")
    public TherapistDto.Response getProfile(@AuthenticationPrincipal UserDetails therapist) {
        return therapistService.getOwnProfile(therapist.getUsername());
    }

    @PutMapping("/profile")
    public TherapistDto.Response updateProfile(@AuthenticationPrincipal UserDetails therapist,
                                               @Valid @RequestBody TherapistDto.SelfUpdateRequest req) {
        return therapistService.updateOwnProfile(therapist.getUsername(), req);
    }

    @GetMapping("/bookings")
    public List<BookingDto.Response> bookings(@AuthenticationPrincipal UserDetails therapist) {
        return bookingService.therapistBookings(therapist.getUsername());
    }

    @PostMapping("/bookings/{id}/approve")
    public BookingDto.Response approve(@AuthenticationPrincipal UserDetails therapist, @PathVariable UUID id) {
        return bookingService.approve(therapist.getUsername(), id);
    }

    @PostMapping("/bookings/{id}/done")
    public BookingDto.Response done(@AuthenticationPrincipal UserDetails therapist, @PathVariable UUID id) {
        return bookingService.markDone(therapist.getUsername(), id);
    }

    // Verify the check-in code the client presents at an in-person session.
    @PostMapping("/bookings/{id}/checkin")
    public BookingDto.Response checkin(@AuthenticationPrincipal UserDetails therapist, @PathVariable UUID id,
                                       @RequestBody java.util.Map<String, String> body) {
        return bookingService.checkIn(therapist.getUsername(), id, body.get("code"));
    }
}

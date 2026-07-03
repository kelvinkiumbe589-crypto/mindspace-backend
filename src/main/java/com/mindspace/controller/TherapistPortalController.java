package com.mindspace.controller;

import com.mindspace.dto.BookingDto;
import com.mindspace.dto.TherapistDto;
import com.mindspace.service.BookingService;
import com.mindspace.service.TherapistService;
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

    public TherapistPortalController(BookingService bookingService, TherapistService therapistService) {
        this.bookingService = bookingService;
        this.therapistService = therapistService;
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
}

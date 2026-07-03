package com.mindspace.controller;

import com.mindspace.dto.BookingDto;
import com.mindspace.service.BookingService;
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

    public TherapistPortalController(BookingService bookingService) {
        this.bookingService = bookingService;
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

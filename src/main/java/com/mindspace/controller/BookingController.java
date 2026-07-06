package com.mindspace.controller;

import com.mindspace.dto.BookingDto;
import com.mindspace.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-side booking endpoints (authenticated user). */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public BookingDto.Response create(@AuthenticationPrincipal UserDetails user,
                                      @Valid @RequestBody BookingDto.CreateRequest req) {
        return bookingService.create(user.getUsername(), req);
    }

    @GetMapping("/me")
    public List<BookingDto.Response> mine(@AuthenticationPrincipal UserDetails user) {
        return bookingService.myBookings(user.getUsername());
    }

    @PostMapping("/{id}/attach-order")
    public BookingDto.Response attachOrder(@AuthenticationPrincipal UserDetails user,
                                           @PathVariable UUID id,
                                           @RequestBody BookingDto.PaidRequest req) {
        return bookingService.attachOrder(user.getUsername(), id, req.getOrderTrackingId());
    }

    @PostMapping("/{id}/paid")
    public BookingDto.Response paid(@AuthenticationPrincipal UserDetails user,
                                    @PathVariable UUID id,
                                    @RequestBody(required = false) BookingDto.PaidRequest req) {
        String tracking = req == null ? null : req.getOrderTrackingId();
        return bookingService.markPaid(user.getUsername(), id, tracking);
    }

    @PostMapping("/{id}/failed")
    public BookingDto.Response failed(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        return bookingService.markFailed(user.getUsername(), id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        bookingService.delete(user.getUsername(), id);
        return Map.of("deleted", true);
    }

    @PostMapping("/{id}/rate")
    public BookingDto.Response rate(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id,
                                    @RequestBody BookingDto.RateRequest req) {
        return bookingService.rate(user.getUsername(), id, req.getRating());
    }
}

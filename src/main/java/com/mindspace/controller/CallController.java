package com.mindspace.controller;

import com.mindspace.service.CallSessionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * The "calling" layer for online sessions: ring the other party, accept/decline,
 * and read the live call state (remaining paid time, whether the peer is online).
 * The actual media negotiation still happens over the /ws/session signaling socket.
 */
@RestController
@RequestMapping("/api/sessions/{bookingId}/call")
public class CallController {

    private final CallSessionService callService;

    public CallController(CallSessionService callService) {
        this.callService = callService;
    }

    /** Ring the other party. */
    @PostMapping
    public Map<String, Object> initiate(@AuthenticationPrincipal UserDetails user,
                                        @PathVariable UUID bookingId) {
        return callService.initiate(user.getUsername(), bookingId);
    }

    @PostMapping("/accept")
    public Map<String, Object> accept(@AuthenticationPrincipal UserDetails user,
                                      @PathVariable UUID bookingId) {
        return callService.accept(user.getUsername(), bookingId);
    }

    @PostMapping("/decline")
    public Map<String, Object> decline(@AuthenticationPrincipal UserDetails user,
                                       @PathVariable UUID bookingId) {
        return callService.decline(user.getUsername(), bookingId);
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal UserDetails user,
                                      @PathVariable UUID bookingId) {
        return callService.cancel(user.getUsername(), bookingId);
    }

    @GetMapping("/state")
    public Map<String, Object> state(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable UUID bookingId) {
        return callService.state(user.getUsername(), bookingId);
    }
}

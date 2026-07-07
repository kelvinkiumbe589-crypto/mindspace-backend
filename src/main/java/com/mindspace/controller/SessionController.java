package com.mindspace.controller;

import com.mindspace.dto.SessionDto;
import com.mindspace.service.SessionChatService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Private per-booking session chat. Both the client and the therapist use these
 * endpoints (identity resolved from the JWT); the service enforces membership.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionChatService sessionChatService;

    public SessionController(SessionChatService sessionChatService) {
        this.sessionChatService = sessionChatService;
    }

    @GetMapping("/{bookingId}/messages")
    public SessionDto.Thread thread(@AuthenticationPrincipal UserDetails user,
                                    @PathVariable UUID bookingId) {
        return sessionChatService.thread(user.getUsername(), bookingId);
    }

    @PostMapping("/{bookingId}/messages")
    public SessionDto.MessageResponse send(@AuthenticationPrincipal UserDetails user,
                                           @PathVariable UUID bookingId,
                                           @Valid @RequestBody SessionDto.SendRequest req) {
        return sessionChatService.send(user.getUsername(), bookingId, req.getText());
    }
}

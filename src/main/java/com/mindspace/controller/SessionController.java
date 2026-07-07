package com.mindspace.controller;

import com.mindspace.dto.SessionDto;
import com.mindspace.service.SessionChatService;
import com.mindspace.service.SessionRoomService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Private per-booking session chat. Both the client and the therapist use these
 * endpoints (identity resolved from the JWT); the service enforces membership.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionChatService sessionChatService;
    private final SessionRoomService sessionRoomService;

    public SessionController(SessionChatService sessionChatService, SessionRoomService sessionRoomService) {
        this.sessionChatService = sessionChatService;
        this.sessionRoomService = sessionRoomService;
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

    // Room join info for an online session: counterparty name + ICE (STUN/TURN) config.
    @GetMapping("/{bookingId}/room")
    public Map<String, Object> room(@AuthenticationPrincipal UserDetails user,
                                    @PathVariable UUID bookingId) {
        SessionRoomService.Participant p = sessionRoomService.authorize(user.getUsername(), bookingId);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("bookingId", p.bookingId());
        out.put("role", p.role());
        out.put("counterpartyName", p.counterpartyName());
        out.putAll(sessionRoomService.iceServers());
        return out;
    }
}

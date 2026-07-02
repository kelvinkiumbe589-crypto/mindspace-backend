package com.mindspace.controller;

import com.mindspace.dto.SupportDto;
import com.mindspace.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    // ── User ──
    @PostMapping
    public SupportDto.MessageResponse send(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SupportDto.SendRequest request) {
        return supportService.sendUserMessage(userDetails.getUsername(), request.getText());
    }

    @GetMapping("/me")
    public List<SupportDto.MessageResponse> myThread(@AuthenticationPrincipal UserDetails userDetails) {
        return supportService.myThread(userDetails.getUsername());
    }

    // ── Admin (ADMIN role only) ──
    @GetMapping("/admin/conversations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SupportDto.Conversation> conversations() {
        return supportService.conversations();
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SupportDto.MessageResponse> thread(@PathVariable UUID userId) {
        return supportService.thread(userId);
    }

    @PostMapping("/admin/{userId}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportDto.MessageResponse reply(
            @PathVariable UUID userId,
            @Valid @RequestBody SupportDto.SendRequest request) {
        return supportService.adminReply(userId, request.getText());
    }
}

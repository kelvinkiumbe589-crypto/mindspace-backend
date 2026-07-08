package com.mindspace.controller;

import com.mindspace.service.TelegramService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    private final TelegramService telegramService;

    @Value("${app.telegram.webhook-secret:}")
    private String webhookSecret;

    public TelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    // Telegram posts updates here. The secret in the path stops strangers calling it.
    @PostMapping("/webhook/{secret}")
    public ResponseEntity<Void> webhook(@PathVariable String secret, @RequestBody Map<String, Object> update) {
        if (webhookSecret == null || webhookSecret.isBlank() || !webhookSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        telegramService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }

    // Logged-in user requests their one-time "connect Telegram" deep link.
    @PostMapping("/link")
    public Map<String, String> link(@AuthenticationPrincipal UserDetails user) {
        return Map.of("url", telegramService.linkUrl(user.getUsername()));
    }
}

package com.mindspace.controller;

import com.mindspace.dto.NotificationDto;
import com.mindspace.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationDto.Item> list(@AuthenticationPrincipal UserDetails userDetails) {
        return notificationService.list(userDetails.getUsername());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails userDetails) {
        return Map.of("count", notificationService.unreadCount(userDetails.getUsername()));
    }

    @PostMapping("/read-all")
    public Map<String, String> markAllRead(@AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markAllRead(userDetails.getUsername());
        return Map.of("status", "ok");
    }
}

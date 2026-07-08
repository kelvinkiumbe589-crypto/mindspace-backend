package com.mindspace.controller;

import com.mindspace.dto.PushDto;
import com.mindspace.service.WebPushService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final WebPushService webPushService;

    public PushController(WebPushService webPushService) {
        this.webPushService = webPushService;
    }

    // Public: the browser needs this VAPID key to subscribe.
    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", webPushService.publicKey() == null ? "" : webPushService.publicKey());
    }

    // Save this device's push subscription for the signed-in user.
    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(@AuthenticationPrincipal UserDetails user,
                                         @RequestBody PushDto.SubscribeRequest req) {
        if (req.getEndpoint() == null || req.getKeys() == null) {
            return Map.of("ok", false);
        }
        webPushService.subscribe(user.getUsername(), req.getEndpoint(), req.getKeys().getP256dh(), req.getKeys().getAuth());
        return Map.of("ok", true);
    }
}

package com.mindspace.controller;

import com.mindspace.service.ReferralService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/referrals")
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    // The signed-in user's invite code, link, invited count, and AI credits.
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserDetails user) {
        return referralService.myInfo(user.getUsername());
    }

    // A newly signed-up user claims the code they arrived with.
    @PostMapping("/attribute")
    public Map<String, Object> attribute(@AuthenticationPrincipal UserDetails user,
                                         @RequestBody Map<String, String> body) {
        return referralService.attribute(user.getUsername(), body.get("code"));
    }
}

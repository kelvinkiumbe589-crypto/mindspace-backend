package com.mindspace.controller;

import com.mindspace.dto.AuthDto;
import com.mindspace.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Self-service profile: read the signed-in user's name/handle and let them change
 * their public messaging username.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final AuthService authService;

    public ProfileController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(authService.profile(user.getUsername()));
    }

    @PutMapping("/handle")
    public ResponseEntity<Map<String, Object>> updateHandle(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody AuthDto.HandleRequest req) {
        return ResponseEntity.ok(authService.updateHandle(user.getUsername(), req.getHandle()));
    }

    // Set/replace the caller's profile photo. Body: { image: <data URL>, visibility: "public"|"private" }.
    @PostMapping("/avatar")
    public ResponseEntity<Map<String, Object>> setAvatar(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                authService.setAvatar(user.getUsername(), body.get("image"), body.get("visibility")));
    }

    // Remove the caller's photo, or stop sharing it when switching to private.
    @DeleteMapping("/avatar")
    public ResponseEntity<Void> removeAvatar(@AuthenticationPrincipal UserDetails user) {
        authService.removeAvatar(user.getUsername());
        return ResponseEntity.noContent().build();
    }
}

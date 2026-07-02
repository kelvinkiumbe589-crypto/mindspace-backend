package com.mindspace.controller;

import com.mindspace.dto.AuthDto;
import com.mindspace.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Step 1: validate + send an email verification code
    @PostMapping("/register")
    public ResponseEntity<AuthDto.PendingResponse> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.PendingResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // Step 2: confirm the code and receive the auth token
    @PostMapping("/verify")
    public ResponseEntity<AuthDto.AuthResponse> verify(
            @Valid @RequestBody AuthDto.VerifyRequest request) {
        return ResponseEntity.ok(authService.verify(request));
    }

    @PostMapping("/resend")
    public ResponseEntity<AuthDto.PendingResponse> resend(
            @Valid @RequestBody AuthDto.ResendRequest request) {
        return ResponseEntity.ok(authService.resend(request));
    }
}

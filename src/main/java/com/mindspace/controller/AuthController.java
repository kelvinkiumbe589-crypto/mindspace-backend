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

    // Step 1: create the account (OTP off) or send an email verification code (OTP on)
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // Returns AuthResponse (trusted device) or PendingResponse (OTP required).
    @PostMapping("/login")
    public ResponseEntity<?> login(
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

    // Password reset: email a code, then set a new password with it.
    @PostMapping("/forgot-password")
    public ResponseEntity<AuthDto.PendingResponse> forgotPassword(
            @Valid @RequestBody AuthDto.ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthDto.AuthResponse> resetPassword(
            @Valid @RequestBody AuthDto.ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}

package com.mindspace.service;

import com.mindspace.dto.AuthDto;
import com.mindspace.model.EmailOtp;
import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import com.mindspace.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;
    private final MailService mailService;

    @Value("${app.admin.emails:}")
    private String adminEmails;

    // When false (e.g. on hosts that block SMTP), skip email OTP — register/login
    // return a token directly. Default true keeps the 2-step verification.
    @Value("${app.otp.enabled:true}")
    private boolean otpEnabled;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager,
                       OtpService otpService,
                       MailService mailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.otpService = otpService;
        this.mailService = mailService;
    }

    private EmailOtp.Purpose parsePurpose(String purpose) {
        try {
            return EmailOtp.Purpose.valueOf(purpose.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid verification purpose");
        }
    }

    private AuthDto.AuthResponse issueToken(User user) {
        applyAdminRole(user);
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthDto.AuthResponse(token, user.getUsername(), user.getEmail(), user.getRole().name());
    }

    // Promote configured emails to ADMIN automatically (comma-separated app.admin.emails)
    private User applyAdminRole(User user) {
        Set<String> admins = Arrays.stream(adminEmails.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase)
                .collect(Collectors.toSet());
        if (admins.contains(user.getEmail().toLowerCase()) && user.getRole() != User.Role.ADMIN) {
            user.setRole(User.Role.ADMIN);
            userRepository.save(user);
        }
        return user;
    }

    // Step 1 of registration: validate, then either create the account directly
    // (OTP off) or stash pending details and email a code (OTP on).
    // Returns AuthResponse (OTP off) or PendingResponse (OTP on).
    public Object register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (!otpEnabled) {
            User user = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .role(User.Role.USER)
                    .build();
            userRepository.save(user);
            mailService.sendWelcomeAsync(user.getEmail(), user.getUsername());
            return issueToken(user);
        }
        otpService.issue(request.getEmail(), EmailOtp.Purpose.REGISTER,
                request.getUsername(), passwordEncoder.encode(request.getPassword()));
        return new AuthDto.PendingResponse(request.getEmail(),
                "We sent a 6-digit verification code to " + request.getEmail());
    }

    // Step 1 of login: validate credentials. A recognised device skips the OTP
    // and gets a token straight away; otherwise we email a code.
    // Returns AuthResponse (trusted device) or PendingResponse (OTP required).
    public Object login(AuthDto.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Staff (admin/therapist) sign in with just a password — no email OTP.
        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.THERAPIST) {
            return issueToken(user);
        }

        // OTP globally disabled (e.g. host blocks email) → password-only login.
        if (!otpEnabled) {
            return issueToken(user);
        }

        if (jwtUtil.isTrustedDevice(request.getDeviceToken(), request.getEmail())) {
            return issueToken(user);
        }

        otpService.issue(request.getEmail(), EmailOtp.Purpose.LOGIN, null, null);
        return new AuthDto.PendingResponse(request.getEmail(),
                "We sent a 6-digit verification code to " + request.getEmail());
    }

    // Step 2: confirm the code and issue the JWT (creating the account for REGISTER).
    public AuthDto.AuthResponse verify(AuthDto.VerifyRequest request) {
        EmailOtp.Purpose purpose = parsePurpose(request.getPurpose());
        EmailOtp otp = otpService.verify(request.getEmail(), purpose, request.getCode());

        User user;
        if (purpose == EmailOtp.Purpose.REGISTER) {
            if (userRepository.existsByEmail(otp.getEmail())) {
                throw new IllegalArgumentException("Email is already registered");
            }
            if (userRepository.existsByUsername(otp.getUsername())) {
                throw new IllegalArgumentException("Username is already taken");
            }
            user = User.builder()
                    .username(otp.getUsername())
                    .email(otp.getEmail())
                    .passwordHash(otp.getPasswordHash())
                    .role(User.Role.USER)
                    .build();
            userRepository.save(user);
            mailService.sendWelcomeAsync(user.getEmail(), user.getUsername());
        } else {
            user = userRepository.findByEmail(otp.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        }

        AuthDto.AuthResponse response = issueToken(user);
        if (request.isTrustDevice()) {
            response.setDeviceToken(jwtUtil.generateDeviceToken(user.getEmail()));
        }
        return response;
    }

    // Step 1 of reset: email a code (only if the account exists — but don't reveal that).
    public AuthDto.PendingResponse forgotPassword(AuthDto.ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail())
                .ifPresent(u -> otpService.issue(request.getEmail(), EmailOtp.Purpose.RESET, null, null));
        return new AuthDto.PendingResponse(request.getEmail(),
                "If an account exists for " + request.getEmail() + ", we've sent a 6-digit reset code.");
    }

    // Step 2 of reset: confirm the code, set the new password, and sign the user in.
    public AuthDto.AuthResponse resetPassword(AuthDto.ResetPasswordRequest request) {
        otpService.verify(request.getEmail(), EmailOtp.Purpose.RESET, request.getCode());
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return issueToken(user);
    }

    public AuthDto.PendingResponse resend(AuthDto.ResendRequest request) {
        otpService.resend(request.getEmail(), parsePurpose(request.getPurpose()));
        return new AuthDto.PendingResponse(request.getEmail(),
                "A new code is on its way to " + request.getEmail());
    }
}

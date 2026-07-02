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

    @Value("${app.admin.emails:}")
    private String adminEmails;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager,
                       OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.otpService = otpService;
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

    // Step 1 of registration: validate, stash pending details, email a code.
    public AuthDto.PendingResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        otpService.issue(request.getEmail(), EmailOtp.Purpose.REGISTER,
                request.getUsername(), passwordEncoder.encode(request.getPassword()));
        return new AuthDto.PendingResponse(request.getEmail(),
                "We sent a 6-digit verification code to " + request.getEmail());
    }

    // Step 1 of login: validate credentials, then email a code.
    public AuthDto.PendingResponse login(AuthDto.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        otpService.issue(request.getEmail(), EmailOtp.Purpose.LOGIN, null, null);
        return new AuthDto.PendingResponse(request.getEmail(),
                "We sent a 6-digit verification code to " + request.getEmail());
    }

    // Step 2: confirm the code and issue the JWT (creating the account for REGISTER).
    public AuthDto.AuthResponse verify(AuthDto.VerifyRequest request) {
        EmailOtp.Purpose purpose = parsePurpose(request.getPurpose());
        EmailOtp otp = otpService.verify(request.getEmail(), purpose, request.getCode());

        if (purpose == EmailOtp.Purpose.REGISTER) {
            if (userRepository.existsByEmail(otp.getEmail())) {
                throw new IllegalArgumentException("Email is already registered");
            }
            if (userRepository.existsByUsername(otp.getUsername())) {
                throw new IllegalArgumentException("Username is already taken");
            }
            User user = User.builder()
                    .username(otp.getUsername())
                    .email(otp.getEmail())
                    .passwordHash(otp.getPasswordHash())
                    .role(User.Role.USER)
                    .build();
            userRepository.save(user);
            return issueToken(user);
        }

        User user = userRepository.findByEmail(otp.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return issueToken(user);
    }

    public AuthDto.PendingResponse resend(AuthDto.ResendRequest request) {
        otpService.resend(request.getEmail(), parsePurpose(request.getPurpose()));
        return new AuthDto.PendingResponse(request.getEmail(),
                "A new code is on its way to " + request.getEmail());
    }
}

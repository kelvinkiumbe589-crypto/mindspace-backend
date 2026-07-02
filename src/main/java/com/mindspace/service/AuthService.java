package com.mindspace.service;

import com.mindspace.dto.AuthDto;
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

    @Value("${app.admin.emails:}")
    private String adminEmails;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
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

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.USER)
                .build();

        userRepository.save(user);
        applyAdminRole(user);

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthDto.AuthResponse(token, user.getUsername(), user.getEmail(), user.getRole().name());
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        applyAdminRole(user);

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthDto.AuthResponse(token, user.getUsername(), user.getEmail(), user.getRole().name());
    }
}

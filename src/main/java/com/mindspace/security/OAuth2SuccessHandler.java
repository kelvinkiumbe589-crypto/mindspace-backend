package com.mindspace.security;

import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import com.mindspace.service.MailService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final MailService mailService;
    // Local encoder avoids a bean cycle (PasswordEncoder is defined in SecurityConfig,
    // which itself depends on this handler). The value is a throwaway — OAuth users log in via Google.
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // The user-app URL to send the browser back to after Google login. Set in the cloud.
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public OAuth2SuccessHandler(JwtUtil jwtUtil, UserRepository userRepository, MailService mailService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.mailService = mailService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String name = oAuth2User.getAttribute("name");
        String email = oAuth2User.getAttribute("email");

        String token = "";
        if (email != null && !email.isBlank()) {
            // Ensure a User row exists so moods/support can attach to it, then issue a JWT.
            // New Google users get a welcome email, just like email/password signups.
            if (userRepository.findByEmail(email).isEmpty()) {
                User u = User.builder()
                        .username(uniqueUsername(name, email))
                        .email(email)
                        .passwordHash(passwordEncoder.encode("oauth2:" + UUID.randomUUID()))
                        .role(User.Role.USER)
                        .build();
                User created = userRepository.save(u);
                mailService.sendWelcomeAsync(created.getEmail(), created.getUsername());
            }
            token = jwtUtil.generateToken(email);
        }

        String redirectUrl = frontendUrl + "/dashboard"
                + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&name=" + URLEncoder.encode(name != null ? name : "", StandardCharsets.UTF_8)
                + "&email=" + URLEncoder.encode(email != null ? email : "", StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }

    private String uniqueUsername(String name, String email) {
        String base = (name != null && !name.isBlank()) ? name : email.split("@")[0];
        if (base.length() > 40) base = base.substring(0, 40);
        String candidate = base;
        if (userRepository.existsByUsername(candidate)) {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }
}

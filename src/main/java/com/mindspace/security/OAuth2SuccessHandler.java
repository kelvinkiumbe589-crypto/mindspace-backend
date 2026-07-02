package com.mindspace.security;

import com.mindspace.model.User;
import com.mindspace.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    public OAuth2SuccessHandler(JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String name = oAuth2User.getAttribute("name");
        String email = oAuth2User.getAttribute("email");

        String token = "";
        if (email != null && !email.isBlank()) {
            // Ensure a User row exists so moods/support can attach to it, then issue a JWT
            userRepository.findByEmail(email).orElseGet(() -> {
                User u = User.builder()
                        .username(uniqueUsername(name, email))
                        .email(email)
                        .passwordHash(passwordEncoder.encode("oauth2:" + UUID.randomUUID()))
                        .role(User.Role.USER)
                        .build();
                return userRepository.save(u);
            });
            token = jwtUtil.generateToken(email);
        }

        String redirectUrl = "http://localhost:5173/dashboard"
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

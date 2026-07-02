package com.mindspace.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String name = oAuth2User.getAttribute("name");
        String email = oAuth2User.getAttribute("email");

        String encodedName = URLEncoder.encode(name != null ? name : "", StandardCharsets.UTF_8);
        String encodedEmail = URLEncoder.encode(email != null ? email : "", StandardCharsets.UTF_8);

        String redirectUrl = "http://localhost:5173/dashboard?name=" + encodedName + "&email=" + encodedEmail;
        response.sendRedirect(redirectUrl);
    }
}
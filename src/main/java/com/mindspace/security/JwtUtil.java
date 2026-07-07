package com.mindspace.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // Trusted-device tokens live much longer than a session and only serve to
    // let a known browser skip the login OTP.
    private static final long DEVICE_EXPIRATION = 30L * 24 * 60 * 60 * 1000; // 30 days

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateDeviceToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("type", "device")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + DEVICE_EXPIRATION))
                .signWith(getSigningKey())
                .compact();
    }

    // Unsubscribe links live in emails, so they must stay valid for a long time.
    private static final long UNSUBSCRIBE_EXPIRATION = 365L * 24 * 60 * 60 * 1000; // 1 year

    /** A long-lived, single-purpose token embedded in reminder emails' unsubscribe link. */
    public String generateUnsubscribeToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("type", "unsub")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + UNSUBSCRIBE_EXPIRATION))
                .signWith(getSigningKey())
                .compact();
    }

    /** Returns the email for a valid unsubscribe token, or null if it's invalid/not an unsub token. */
    public String parseUnsubscribeEmail(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims c = parseClaims(token);
            return "unsub".equals(c.get("type", String.class)) ? c.getSubject() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** True if the token is a valid, unexpired device token bound to this email. */
    public boolean isTrustedDevice(String token, String email) {
        if (token == null || token.isBlank() || email == null) return false;
        try {
            Claims c = parseClaims(token);
            return "device".equals(c.get("type", String.class))
                    && email.equalsIgnoreCase(c.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Device tokens must never be accepted as session/access tokens. */
    public boolean isDeviceToken(String token) {
        try {
            return "device".equals(parseClaims(token).get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

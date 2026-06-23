package com.taskhub.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT service for access token generation and validation.
 * Access tokens are short-lived (15 min), contain jti for revocation support.
 */
@Service
public class JwtService {

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-expiration-days}")
    private int refreshTokenExpirationDays;

    @Value("${spring.application.name:taskhub}")
    private String issuer;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate a short-lived access token (15 min default).
     * Includes jti claim for per-token revocation support.
     */
    public String generateAccessToken(Long userId, String email, String role) {
        String jti = UUID.randomUUID().toString();
        return Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("tokenType", TOKEN_TYPE_ACCESS)
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getKey())
                .compact();
    }

    /**
     * Generate a long-lived refresh token (7 days default).
     * Stored as opaque UUID in DB, not signed JWT.
     * This method returns the raw token string for DB storage.
     */
    public String generateRefreshTokenRaw() {
        return UUID.randomUUID().toString();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public String getTokenId(String token) {
        return getClaims(token).getId();
    }

    public String getTokenType(String token) {
        return getClaims(token).get("tokenType", String.class);
    }

    public boolean isAccessToken(String token) {
        try {
            return TOKEN_TYPE_ACCESS.equals(getTokenType(token));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try { getClaims(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .clock(() -> Date.from(Instant.now()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Instant getIssuedAt(String token) {
        return getClaims(token).getIssuedAt().toInstant();
    }

    public Instant getExpiration(String token) {
        return getClaims(token).getExpiration().toInstant();
    }

    public int getRefreshTokenExpirationDays() {
        return refreshTokenExpirationDays;
    }

    /**
     * Generate a secure random token for password reset or email verification.
     * Uses UUID v4 format.
     */
    public String generateSecureToken() {
        return UUID.randomUUID().toString();
    }
}

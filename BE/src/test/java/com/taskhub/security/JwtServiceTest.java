package com.taskhub.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String JWT_SECRET = "dev-only-default-secret-change-me-min-32-chars!!";
    private static final long ACCESS_EXP_MS = 900_000L;
    private static final int REFRESH_EXP_DAYS = 7;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs", ACCESS_EXP_MS);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpirationDays", REFRESH_EXP_DAYS);
        ReflectionTestUtils.setField(jwtService, "issuer", "taskhub-backend");
    }

    // ── generateAccessToken ────────────────────────────────

    @Test
    void generateAccessToken_ReturnsValidJwt() {
        String token = jwtService.generateAccessToken(1L, "alice@test.com", "STUDENT");

        assertNotNull(token);
        assertTrue(token.contains("."));
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void generateAccessToken_CanExtractUserId() {
        String token = jwtService.generateAccessToken(42L, "bob@test.com", "HIRER");

        assertDoesNotThrow(() -> jwtService.getUserIdFromToken(token));
        assertEquals(42L, jwtService.getUserIdFromToken(token));
    }

    @Test
    void generateAccessToken_ContainsJti() {
        String token = jwtService.generateAccessToken(1L, "x@test.com", "ADMIN");

        assertDoesNotThrow(() -> jwtService.getTokenId(token));
        assertNotNull(jwtService.getTokenId(token));
    }

    @Test
    void generateAccessToken_HasCorrectTokenType() {
        String token = jwtService.generateAccessToken(1L, "x@test.com", "STUDENT");

        assertEquals("access", jwtService.getTokenType(token));
    }

    @Test
    void generateAccessToken_IsAccessToken() {
        String token = jwtService.generateAccessToken(1L, "x@test.com", "STUDENT");

        assertTrue(jwtService.isAccessToken(token));
    }

    // ── generateRefreshTokenRaw ─────────────────────────────

    @Test
    void generateRefreshTokenRaw_ReturnsUuid() {
        String raw = jwtService.generateRefreshTokenRaw();

        assertDoesNotThrow(() -> UUID.fromString(raw));
    }

    @Test
    void generateRefreshTokenRaw_UniquePerCall() {
        String raw1 = jwtService.generateRefreshTokenRaw();
        String raw2 = jwtService.generateRefreshTokenRaw();

        assertNotEquals(raw1, raw2);
    }

    // ── validateToken ───────────────────────────────────────

    @Test
    void validateToken_ValidToken_ReturnsTrue() {
        String token = jwtService.generateAccessToken(1L, "x@test.com", "STUDENT");

        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void validateToken_TamperedToken_ReturnsFalse() {
        String token = jwtService.generateAccessToken(1L, "x@test.com", "STUDENT");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtService.validateToken(tampered));
    }

    @Test
    void validateToken_InvalidFormat_ReturnsFalse() {
        assertFalse(jwtService.validateToken("not.a.jwt"));
        assertFalse(jwtService.validateToken(""));
    }

    @Test
    void validateToken_WrongSecret_ReturnsFalse() {
        JwtService otherService = new JwtService();
        ReflectionTestUtils.setField(otherService, "jwtSecret", "completely-different-secret-that-is-32-chars!");
        ReflectionTestUtils.setField(otherService, "accessTokenExpirationMs", ACCESS_EXP_MS);
        ReflectionTestUtils.setField(otherService, "refreshTokenExpirationDays", REFRESH_EXP_DAYS);
        ReflectionTestUtils.setField(otherService, "issuer", "taskhub-backend");

        String token = otherService.generateAccessToken(1L, "x@test.com", "STUDENT");

        assertFalse(jwtService.validateToken(token));
    }

    // ── generateSecureToken ────────────────────────────────

    @Test
    void generateSecureToken_ReturnsValidUuid() {
        String token = jwtService.generateSecureToken();

        assertDoesNotThrow(() -> UUID.fromString(token));
    }

    @Test
    void generateSecureToken_UniquePerCall() {
        String t1 = jwtService.generateSecureToken();
        String t2 = jwtService.generateSecureToken();

        assertNotEquals(t1, t2);
    }

    // ── getRefreshTokenExpirationDays ─────────────────────

    @Test
    void getRefreshTokenExpirationDays_ReturnsConfiguredValue() {
        assertEquals(7, jwtService.getRefreshTokenExpirationDays());
    }
}

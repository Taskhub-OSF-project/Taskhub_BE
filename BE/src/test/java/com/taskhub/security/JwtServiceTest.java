package com.taskhub.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(null);
        ReflectionTestUtils.setField(jwtService, "jwtSecret",
                "test-jwt-secret-minimum-32-characters-long!!");
        ReflectionTestUtils.setField(jwtService, "accessExpirationMs", 900_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", 604_800_000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "taskhub-test");
    }

    @Test
    void generateAccessToken_containsAccessType() {
        String token = jwtService.generateAccessToken(1L, "user@test.com", "STUDENT");
        assertTrue(jwtService.validateToken(token));
        assertEquals(JwtService.TYPE_ACCESS, jwtService.getTokenType(token));
        assertEquals(1L, jwtService.getUserIdFromToken(token));
    }

    @Test
    void generateRefreshToken_containsRefreshType() {
        String token = jwtService.generateRefreshToken(42L);
        assertTrue(jwtService.validateToken(token));
        assertEquals(JwtService.TYPE_REFRESH, jwtService.getTokenType(token));
        assertEquals(42L, jwtService.getUserIdFromToken(token));
    }

    @Test
    void validateToken_rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(1L, "user@test.com", "STUDENT");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertFalse(jwtService.validateToken(tampered));
    }

    @Test
    void validateToken_rejectsInvalidFormat() {
        assertFalse(jwtService.validateToken("not.a.jwt"));
        assertFalse(jwtService.validateToken(""));
    }
}

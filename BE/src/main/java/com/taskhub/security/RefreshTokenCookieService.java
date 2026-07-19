package com.taskhub.security;

import com.taskhub.dto.response.AuthResponse;
import com.taskhub.exception.TaskHubException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieService {
    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public RefreshTokenCookieService(
            @Value("${app.auth.refresh-cookie.name:taskhub_refresh}") String cookieName,
            @Value("${app.auth.refresh-cookie.secure:true}") boolean secure,
            @Value("${app.auth.refresh-cookie.same-site:None}") String sameSite,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = Duration.ofMillis(refreshExpirationMs);
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException("SameSite=None refresh cookies must be Secure");
        }
    }

    public AuthResponse moveRefreshTokenToCookie(HttpServletResponse response, AuthResponse authResponse) {
        if (authResponse == null || authResponse.getRefreshToken() == null
                || authResponse.getRefreshToken().isBlank()) {
            throw TaskHubException.internalError("Refresh token was not issued");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(authResponse.getRefreshToken(), maxAge).toString());
        authResponse.setRefreshToken(null);
        return authResponse;
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(age)
                .build();
    }
}

package com.taskhub.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/** Issues and verifies a signed, HttpOnly marker for devices that completed login OTP. */
@Component
public class TrustedDeviceService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] signingKey;
    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final long maxAgeSeconds;

    public TrustedDeviceService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.auth.trusted-device-cookie.name:taskhub_trusted}") String cookieName,
            @Value("${app.auth.trusted-device-cookie.secure:true}") boolean secure,
            @Value("${app.auth.trusted-device-cookie.same-site:None}") String sameSite,
            @Value("${app.auth.trusted-device-cookie.max-age-seconds:2592000}") long maxAgeSeconds) {
        this.signingKey = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAgeSeconds = maxAgeSeconds;
        if (maxAgeSeconds <= 0) throw new IllegalArgumentException("Trusted-device max age must be positive");
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalStateException("SameSite=None trusted-device cookies must be Secure");
        }
    }

    public void remember(HttpServletResponse response, Long userId) {
        long expiresAt = Instant.now().getEpochSecond() + maxAgeSeconds;
        String payload = userId + ":" + expiresAt;
        String token = encode(payload) + "." + encode(sign(payload));
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public boolean isTrusted(String token, Long expectedUserId) {
        if (token == null || token.isBlank() || expectedUserId == null) return false;
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) return false;
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) return false;
            String[] claims = payload.split(":", -1);
            if (claims.length != 2 || !expectedUserId.equals(Long.valueOf(claims[0]))) return false;
            return Long.parseLong(claims[1]) > Instant.now().getEpochSecond();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign trusted-device token", ex);
        }
    }

    private String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}

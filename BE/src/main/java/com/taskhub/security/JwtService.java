package com.taskhub.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Service tạo và kiểm tra JWT.
 * Thuộc module Security, được gọi từ AuthService và JwtAuthFilter.
 *
 * <p>Phát hành hai loại token tách biệt qua claim {@code type}:
 * <ul>
 *   <li>{@code access} — vòng đời ngắn, dùng cho mọi request API.</li>
 *   <li>{@code refresh} — vòng đời dài, chỉ dùng để xin cấp lại access token.</li>
 * </ul>
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    /** Secret mặc định chỉ dành cho dev — chặn boot ở profile khác (xem validateSecret). */
    static final String DEV_DEFAULT_SECRET = "dev-only-default-secret-change-me-min-32-chars!!";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.jwt.issuer:taskhub}")
    private String issuer;

    private final Environment environment;

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Fail-fast khi boot: chặn secret yếu/mặc định ở môi trường không phải dev.
     */
    @PostConstruct
    void validateSecret() {
        boolean bypassCheck = false;
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)
                    || "supabase".equalsIgnoreCase(profile)
                    || "postgres".equalsIgnoreCase(profile)) {
                bypassCheck = true;
                break;
            }
        }
        if (bypassCheck) return;

        int bytes = jwtSecret == null ? 0 : jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (jwtSecret == null || DEV_DEFAULT_SECRET.equals(jwtSecret) || bytes < 32) {
            throw new IllegalStateException(
                    "Insecure JWT secret for a non-dev profile. Set a strong APP_JWT_SECRET "
                            + "(>= 32 bytes, not the dev default).");
        }
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public long getAccessExpirationMs() { return accessExpirationMs; }

    public long getRefreshExpirationMs() { return refreshExpirationMs; }

    /**
     * Tạo access token (ngắn hạn) mang email/role để downstream phân quyền.
     */
    public String generateAccessToken(Long userId, String email, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessExpirationMs))
                .signWith(getKey())
                .compact();
    }

    /**
     * Tạo refresh token (dài hạn). Chỉ chứa subject + type, không mang quyền.
     */
    public String generateRefreshToken(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshExpirationMs))
                .signWith(getKey())
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    /**
     * Lấy loại token ({@code access} / {@code refresh}); null nếu không có claim.
     */
    public String getTokenType(String token) {
        return getClaims(token).get(CLAIM_TYPE, String.class);
    }

    /**
     * Kiểm tra token có hợp lệ và chưa hết hạn hay không.
     */
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
}

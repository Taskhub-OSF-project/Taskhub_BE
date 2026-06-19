package com.taskhub.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    private static final int AUTH_REQUESTS_PER_MINUTE = 20;
    private static final int AUTH_REQUESTS_PER_HOUR = 100;
    private static final int LOGIN_REQUESTS_PER_MINUTE = 5;

    private Bucket createAuthBucket() {
        Bandwidth perMinute = Bandwidth.classic(AUTH_REQUESTS_PER_MINUTE, Refill.greedy(AUTH_REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
        Bandwidth perHour = Bandwidth.classic(AUTH_REQUESTS_PER_HOUR, Refill.greedy(AUTH_REQUESTS_PER_HOUR, Duration.ofHours(1)));
        return Bucket.builder()
                .addLimit(perMinute)
                .addLimit(perHour)
                .build();
    }

    private Bucket createLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(LOGIN_REQUESTS_PER_MINUTE, Refill.greedy(LOGIN_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))))
                .build();
    }

    private Bucket getBucket(String key, boolean isLogin) {
        return authBuckets.computeIfAbsent(key, k -> isLogin ? createLoginBucket() : createAuthBucket());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!isAuthPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        boolean isLogin = path.endsWith("/login") && "POST".equalsIgnoreCase(request.getMethod());
        Bucket bucket = getBucket(clientKey, isLogin);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientKey, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}");
        }
    }

    private boolean isAuthPath(String path) {
        return path.startsWith("/api/auth");
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

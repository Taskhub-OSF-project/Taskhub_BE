package com.taskhub.security;

import com.taskhub.config.RateLimitProperties;
import com.taskhub.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiting for sensitive auth endpoints using Bucket4j token buckets.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotPasswordBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> refreshBuckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/auth/login")
                && !path.equals("/api/auth/forgot-password")
                && !path.equals("/api/auth/refresh");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String path = request.getRequestURI();

        Bucket bucket = switch (path) {
            case "/api/auth/login" -> loginBuckets.computeIfAbsent(clientIp, k -> newBucket(
                    rateLimitProperties.getLoginPerMinute(), Duration.ofMinutes(1)));
            case "/api/auth/forgot-password" -> forgotPasswordBuckets.computeIfAbsent(clientIp, k -> newBucket(
                    rateLimitProperties.getForgotPasswordPerHour(), Duration.ofHours(1)));
            case "/api/auth/refresh" -> refreshBuckets.computeIfAbsent(clientIp, k -> newBucket(
                    rateLimitProperties.getRefreshPerMinute(), Duration.ofMinutes(1)));
            default -> null;
        };

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error("Too many requests. Please try again later.", "RATE_LIMITED", null));
            return;
        }

        chain.doFilter(request, response);
    }

    private static Bucket newBucket(int capacity, Duration period) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, period)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

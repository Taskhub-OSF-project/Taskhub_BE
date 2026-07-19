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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IP-based rate limiting for sensitive auth endpoints using Bucket4j token buckets.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, TimedBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();
    private static final long IDLE_EVICTION_MILLIS = Duration.ofHours(2).toMillis();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return resolveLimit(path) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        String path = request.getRequestURI();

        Limit limit = resolveLimit(path);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();
        String key = path + ':' + resolveRateLimitSubject(path, clientIp);
        TimedBucket timed = buckets.compute(key, (ignored, existing) -> {
            if (existing == null) {
                return new TimedBucket(newBucket(limit.capacity(), limit.period()), now);
            }
            existing.lastAccessMillis = now;
            return existing;
        });
        if ((requestCounter.incrementAndGet() & 255) == 0) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMillis > IDLE_EVICTION_MILLIS);
        }

        if (!timed.bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error("Too many requests. Please try again later.", "RATE_LIMITED", null));
            return;
        }

        chain.doFilter(request, response);
    }

    private Limit resolveLimit(String path) {
        return switch (path) {
            case "/api/auth/login" -> new Limit(rateLimitProperties.getLoginPerMinute(), Duration.ofMinutes(1));
            case "/api/auth/refresh" -> new Limit(rateLimitProperties.getRefreshPerMinute(), Duration.ofMinutes(1));
            case "/api/auth/register" -> new Limit(rateLimitProperties.getRegisterPerHour(), Duration.ofHours(1));
            case "/api/auth/forgot-password", "/api/auth/recover-account",
                 "/api/auth/recover-password/request", "/api/auth/recover-password/confirm",
                 "/api/auth/reset-password", "/api/auth/email-otp/verify",
                 "/api/auth/email-otp/resend" -> new Limit(rateLimitProperties.getRecoveryPerHour(), Duration.ofHours(1));
            case "/api/ai/public/chat" -> new Limit(rateLimitProperties.getPublicAiPerMinute(), Duration.ofMinutes(1));
            default -> path.startsWith("/api/ai/")
                    ? new Limit(rateLimitProperties.getAiPerMinute(), Duration.ofMinutes(1))
                    : null;
        };
    }

    private static Bucket newBucket(int capacity, Duration period) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, period)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static String resolveRateLimitSubject(String path, String clientIp) {
        if (path.startsWith("/api/ai/") && !"/api/ai/public/chat".equals(path)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof com.taskhub.entity.User user) {
                return "user:" + user.getId();
            }
        }
        return "ip:" + clientIp;
    }

    private record Limit(int capacity, Duration period) {}

    private static final class TimedBucket {
        private final Bucket bucket;
        private volatile long lastAccessMillis;

        private TimedBucket(Bucket bucket, long lastAccessMillis) {
            this.bucket = bucket;
            this.lastAccessMillis = lastAccessMillis;
        }
    }
}

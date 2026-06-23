package com.taskhub.security;

import com.taskhub.entity.User;
import com.taskhub.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/**
 * Filter đọc JWT từ header và set Authentication cho request.
 * Thuộc module Security, chạy trước các controller protected.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/error",
            "/favicon.ico",
            "/swagger-ui.html",
            "/swagger-ui/",
            "/swagger-ui",
            "/v3/api-docs",
            "/v3/api-docs/",
            "/v3/api-docs/swagger-config",
            "/api/auth/"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.equals("/")
                || path.equals("/error")
                || path.equals("/favicon.ico")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/auth");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String clientIp = resolveClientIp(request);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (!jwtService.validateToken(token)) {
                log.warn("[AUTH] Invalid/expired token from IP={}, path={}", clientIp, request.getRequestURI());
                chain.doFilter(request, response);
                return;
            }

            Long userId = jwtService.getUserIdFromToken(token);
            userRepository.findById(userId).ifPresentOrElse(
                    user -> {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    },
                    () -> log.warn("[AUTH] Token valid but user not found: id={}, IP={}", userId, clientIp)
            );
        }

        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }
}

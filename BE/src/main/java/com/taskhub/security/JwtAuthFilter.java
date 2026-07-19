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
import org.springframework.beans.factory.annotation.Value;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${app.auth.require-email-verification:false}")
    private boolean requireEmailVerification;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // /api/auth/logout requires authentication, so the filter must run.
        if (path.equals("/api/auth/logout")) {
            return false;
        }

        return path.equals("/")
                || path.equals("/error")
                || path.equals("/favicon.ico")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/auth");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtService.validateToken(token)
                    && JwtService.TYPE_ACCESS.equals(jwtService.getTokenType(token))) {
                Long userId = jwtService.getUserIdFromToken(token);
                userRepository.findById(userId)
                        .filter(user -> !Boolean.TRUE.equals(user.getIsBanned()))
                        .filter(user -> !requireEmailVerification || user.isEmailVerified())
                        .ifPresent(user -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }

        chain.doFilter(request, response);
    }
}

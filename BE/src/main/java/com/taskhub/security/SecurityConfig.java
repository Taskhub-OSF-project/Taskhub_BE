package com.taskhub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.config.CorsProperties;
import com.taskhub.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security configuration: stateless JWT, production CORS, secure headers.
 *
 * <p><b>CSRF:</b> Disabled globally because protected API calls use an in-memory Bearer token,
 * which browsers do not attach automatically. The two endpoints that consume the HttpOnly refresh
 * cookie require {@code X-Requested-With: XMLHttpRequest}; this forces a CORS preflight and blocks
 * cross-site form submissions from untrusted origins.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean devProfile = List.of(environment.getActiveProfiles()).contains("dev");

        http
                .cors(c -> c.configurationSource(corsSource()))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> {
                    if (devProfile) {
                        h.frameOptions(f -> f.sameOrigin());
                    } else {
                        h.frameOptions(f -> f.deny());
                    }
                    h.contentTypeOptions(c -> {});
                    h.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000));
                    h.referrerPolicy(r -> r.policy(
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                    .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/api/auth/logout").authenticated();
                    a.requestMatchers("/api/files/**").authenticated();
                    a.requestMatchers(
                            "/",
                            "/error",
                            "/api/health",
                            "/api/auth/**",
                            "/api/ai/public/chat",
                            "/api/reviews/latest"
                    ).permitAll();
                    a.requestMatchers("/api/ai/**").authenticated();
                    if (devProfile) {
                        a.requestMatchers(
                                "/h2-console/**",
                                "/v3/api-docs/**"
                        ).permitAll();
                    }
                    a.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private CorsConfigurationSource corsSource() {
        var config = new CorsConfiguration();
        List<String> origins = corsProperties.getAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            config.setAllowedOriginPatterns(origins.stream()
                .map(origin -> origin.replace("localhost:5173", "localhost:*").replace("127.0.0.1:5173", "127.0.0.1:*"))
                .toList());
        } else {
            config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> writeJson(response, HttpStatus.FORBIDDEN,
                ApiResponse.error("Access denied", "FORBIDDEN", null));
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException ex)
            throws IOException {
        writeJson(response, HttpStatus.UNAUTHORIZED,
                ApiResponse.error("Authentication required", "UNAUTHORIZED", null));
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, ApiResponse<?> body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}

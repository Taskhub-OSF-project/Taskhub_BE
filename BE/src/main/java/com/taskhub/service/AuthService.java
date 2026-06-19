package com.taskhub.service;

import com.taskhub.dto.request.LoginRequest;
import com.taskhub.dto.request.RefreshTokenRequest;
import com.taskhub.dto.request.RegisterRequest;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.entity.RefreshToken;
import com.taskhub.entity.User;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.JwtService;
import com.taskhub.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.refresh.expiration-days:30}")
    private int refreshTokenExpirationDays;

    @Value("${app.refresh.max-per-user:5}")
    private int maxRefreshTokensPerUser;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw TaskHubException.badRequest("Email already registered");

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .university(trimToNull(req.getUniversity()))
                .major(trimToNull(req.getMajor()))
                .role(req.getRole())
                .build();
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> TaskHubException.badRequest("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw TaskHubException.badRequest("Invalid credentials");

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        String providedHash = TokenHasher.hash(req.getRefreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(providedHash)
                .orElseThrow(() -> TaskHubException.unauthorized("Invalid refresh token"));

        if (!storedToken.isValid()) {
            throw TaskHubException.unauthorized("Refresh token has expired or been revoked");
        }

        User user = storedToken.getUser();

        // Rotate: revoke old token and issue new one
        String newRefreshToken = jwtService.generateRefreshToken();
        String newHash = TokenHasher.hash(newRefreshToken);

        // Revoke the old token and all other active tokens (one session per device)
        refreshTokenRepository.revokeAllUserTokens(user.getId(), newHash);

        // Create new refresh token
        RefreshToken newToken = RefreshToken.builder()
                .tokenHash(newHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(newToken);

        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest req) {
        String providedHash = TokenHasher.hash(req.getRefreshToken());
        int revoked = refreshTokenRepository.revokeByTokenHash(providedHash);
        if (revoked == 0) {
            // Don't fail silently - still clear the client-side token
        }
    }

    @Transactional
    public void logoutAll(Long userId) {
        String placeholderHash = "logout-all-" + userId;
        refreshTokenRepository.revokeAllUserTokens(userId, placeholderHash);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken();

        // Enforce max tokens limit
        enforceRefreshTokenLimit(user.getId());

        RefreshToken storedToken = RefreshToken.builder()
                .tokenHash(TokenHasher.hash(refreshToken))
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(storedToken);

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    private void enforceRefreshTokenLimit(Long userId) {
        // Purge expired tokens first
        refreshTokenRepository.purgeExpiredTokens(userId);

        // Count active tokens for this user
        long activeCount = refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(RefreshToken::isValid)
                .count();

        if (activeCount >= maxRefreshTokensPerUser) {
            // Revoke the oldest valid token
            refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(RefreshToken::isValid)
                    .reduce((first, second) -> second)
                    .ifPresent(oldest -> refreshTokenRepository.revokeByTokenHash(oldest.getTokenHash()));
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

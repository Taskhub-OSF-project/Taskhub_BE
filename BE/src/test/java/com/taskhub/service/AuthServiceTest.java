package com.taskhub.service;

import com.taskhub.BaseIntegrationTest;
import com.taskhub.dto.request.LoginRequest;
import com.taskhub.dto.request.RegisterRequest;
import com.taskhub.dto.request.RefreshTokenRequest;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.entity.PasswordResetToken;
import com.taskhub.entity.RefreshToken;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.PasswordResetTokenRepository;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.util.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest extends BaseIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ── Register ────────────────────────────────────────────

    @Test
    void register_Success() {
        RegisterRequest req = RegisterRequest.builder()
                .email("newuser@test.com")
                .password("Password123!")
                .fullName("New User")
                .role(Role.STUDENT)
                .build();

        AuthResponse resp = authService.register(req);

        assertNotNull(resp.getToken());
        assertNotNull(resp.getRefreshToken());
        assertEquals("newuser@test.com", resp.getEmail());
        assertEquals(Role.STUDENT, resp.getRole());

        User saved = userRepository.findByEmail("newuser@test.com").orElseThrow();
        assertFalse(saved.getIsVerified());
    }

    @Test
    void register_DuplicateEmail_Throws() {
        userRepository.save(User.builder()
                .email("dup@test.com").password("x").fullName("X").role(Role.STUDENT).build());

        RegisterRequest req = RegisterRequest.builder()
                .email("dup@test.com").password("Pass1!").fullName("X").role(Role.STUDENT).build();

        assertThrows(TaskHubException.class, () -> authService.register(req));
    }

    // ── Login ─────────────────────────────────────────────

    @Test
    void login_Success() {
        String rawPw = "Password123!";
        userRepository.save(User.builder()
                .email("login@test.com").password(passwordEncoder.encode(rawPw))
                .fullName("Login User").role(Role.STUDENT).isVerified(true).build());

        AuthResponse resp = authService.login(new LoginRequest("login@test.com", rawPw));

        assertNotNull(resp.getToken());
        assertNotNull(resp.getRefreshToken());
        assertEquals("login@test.com", resp.getEmail());
    }

    @Test
    void login_WrongPassword_Throws() {
        userRepository.save(User.builder()
                .email("wrongpw@test.com").password(passwordEncoder.encode("Correct1!"))
                .fullName("X").role(Role.STUDENT).isVerified(true).build());

        assertThrows(TaskHubException.class, () ->
                authService.login(new LoginRequest("wrongpw@test.com", "WrongPass1!")));
    }

    @Test
    void login_UnknownEmail_Throws() {
        assertThrows(TaskHubException.class, () ->
                authService.login(new LoginRequest("nobody@test.com", "AnyPass1!")));
    }

    // ── Refresh Token ─────────────────────────────────────

    @Test
    void refreshToken_Success() {
        User user = userRepository.save(User.builder()
                .email("refresh@test.com").password("x").fullName("X").role(Role.STUDENT).isVerified(true).build());
        setAuth(user);

        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(TokenHasher.hash("old-refresh-token"))
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());

        AuthResponse resp = authService.refreshToken(new RefreshTokenRequest("old-refresh-token"));

        assertNotNull(resp.getToken());
        assertNotNull(resp.getRefreshToken());
        assertNotEquals("old-refresh-token", resp.getRefreshToken());

        RefreshToken old = refreshTokenRepository.findByTokenHash(TokenHasher.hash("old-refresh-token")).orElseThrow();
        assertTrue(old.getRevoked());
    }

    @Test
    void refreshToken_ExpiredToken_Throws() {
        User user = userRepository.save(User.builder()
                .email("expired@test.com").password("x").fullName("X").role(Role.STUDENT).isVerified(true).build());
        setAuth(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(TokenHasher.hash("expired-token"))
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build());

        assertThrows(TaskHubException.class, () ->
                authService.refreshToken(new RefreshTokenRequest("expired-token")));
    }

    // ── Logout ─────────────────────────────────────────────

    @Test
    void logout_RevokesToken() {
        User user = userRepository.save(User.builder()
                .email("logout@test.com").password("x").fullName("X").role(Role.STUDENT).isVerified(true).build());
        setAuth(user);

        RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(TokenHasher.hash("logout-token"))
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());

        authService.logout(new RefreshTokenRequest("logout-token"));

        RefreshToken revoked = refreshTokenRepository.findByTokenHash(TokenHasher.hash("logout-token")).orElseThrow();
        assertTrue(revoked.getRevoked());
    }

    @Test
    void logoutAll_RevokesAllTokens() {
        User user = userRepository.save(User.builder()
                .email("logoutall@test.com").password("x").fullName("X").role(Role.STUDENT).isVerified(true).build());
        setAuth(user);

        refreshTokenRepository.saveAll(List.of(
                RefreshToken.builder().tokenHash(TokenHasher.hash("token1")).user(user)
                        .expiresAt(LocalDateTime.now().plusDays(7)).build(),
                RefreshToken.builder().tokenHash(TokenHasher.hash("token2")).user(user)
                        .expiresAt(LocalDateTime.now().plusDays(7)).build()
        ));

        authService.logoutAll(user.getId());

        List<RefreshToken> active = refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertEquals(0, active.stream().filter(t -> !t.getRevoked()).count());
    }

    // ── Password Change ─────────────────────────────────────

    @Test
    void changePassword_Success() {
        String oldPw = "OldPass1!";
        User user = userRepository.save(User.builder()
                .email("changepw@test.com").password(passwordEncoder.encode(oldPw))
                .fullName("X").role(Role.STUDENT).isVerified(true).build());

        authService.changePassword(user.getId(), oldPw, "NewPass2!");

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewPass2!", updated.getPassword()));
    }

    @Test
    void changePassword_WrongCurrent_Throws() {
        User user = userRepository.save(User.builder()
                .email("wrongpw2@test.com").password(passwordEncoder.encode("Correct1!"))
                .fullName("X").role(Role.STUDENT).isVerified(true).build());

        assertThrows(TaskHubException.class, () ->
                authService.changePassword(user.getId(), "WrongPass1!", "NewPass2!"));
    }

    // ── Password Reset ──────────────────────────────────────

    @Test
    void forgotPassword_ExistingUser_CreatesToken() {
        User user = userRepository.save(User.builder()
                .email("forgotpw@test.com").password("x").fullName("X").role(Role.STUDENT).build());
        Long userId = user.getId();

        var response = authService.forgotPassword("forgotpw@test.com");

        assertTrue(passwordResetTokenRepository.findValidTokenByUserId(userId).isPresent());
        // In dev profile, SMTP is disabled — the response should include the reset link/token
        assertNotNull(response);
        assertFalse(response.isEmailSent());
        assertNotNull(response.getResetLink());
        assertNotNull(response.getToken());
    }

    @Test
    void forgotPassword_UnknownEmail_NoException() {
        var response = assertDoesNotThrow(() -> authService.forgotPassword("ghost@nobody.com"));
        assertNotNull(response);
        // For unknown email, neither link nor token is exposed (enumeration-safe)
        assertFalse(response.isEmailSent());
        assertNull(response.getResetLink());
        assertNull(response.getToken());
    }

    @Test
    void resetPassword_Success() {
        User user = userRepository.save(User.builder()
                .email("resetpw@test.com").password(passwordEncoder.encode("OldPass1!"))
                .fullName("X").role(Role.STUDENT).isVerified(true).build());

        String token = "reset-token-abc123";
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .tokenHash(TokenHasher.hash(token))
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .build());

        authService.resetPassword(token, "NewResetPass2!");

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewResetPass2!", updated.getPassword()));
    }

    @Test
    void resetPassword_ExpiredToken_Throws() {
        User user = userRepository.save(User.builder()
                .email("expiredreset@test.com").password(passwordEncoder.encode("OldPass1!"))
                .fullName("X").role(Role.STUDENT).isVerified(true).build());

        String token = "expired-reset-token";
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .tokenHash(TokenHasher.hash(token))
                .user(user)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build());

        assertThrows(TaskHubException.class, () -> authService.resetPassword(token, "NewPass2!"));
    }

    // ── Email Verification ─────────────────────────────────

    @Test
    void verifyEmail_Success() {
        String token = "verify-token-xyz";
        User user = userRepository.save(User.builder()
                .email("verify@test.com").password("x").fullName("X").role(Role.STUDENT).isVerified(false).build());

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .tokenHash(TokenHasher.hash(token))
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build());

        authService.verifyEmail(token);

        User verified = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(verified.getIsVerified());
    }

    @Test
    void verifyEmail_ExpiredToken_Throws() {
        String token = "expired-verify-token";
        User user = userRepository.save(User.builder()
                .email("expiredver@test.com").password("x").fullName("X").role(Role.STUDENT).isVerified(false).build());

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .tokenHash(TokenHasher.hash(token))
                .user(user)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .used(false)
                .build());

        assertThrows(TaskHubException.class, () -> authService.verifyEmail(token));
    }
}

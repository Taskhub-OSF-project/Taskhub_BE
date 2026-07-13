package com.taskhub.service;

import com.taskhub.dto.request.ForgotPasswordRequest;
import com.taskhub.dto.request.LoginRequest;
import com.taskhub.dto.request.LogoutRequest;
import com.taskhub.dto.request.PasswordResetConfirmRequest;
import com.taskhub.dto.request.PasswordResetRequest;
import com.taskhub.dto.request.RefreshTokenRequest;
import com.taskhub.dto.request.RegisterRequest;
import com.taskhub.dto.request.RecoverAccountRequest;
import com.taskhub.dto.request.ResetPasswordRequest;
import com.taskhub.dto.request.VerifyEmailRequest;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.entity.OtpToken;
import com.taskhub.entity.RefreshToken;
import com.taskhub.entity.User;
import com.taskhub.entity.VerificationToken;
import com.taskhub.enums.VerificationTokenType;
import com.taskhub.repository.OtpTokenRepository;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.repository.VerificationTokenRepository;
import com.taskhub.security.JwtService;
import com.taskhub.service.mail.MailService;
import com.taskhub.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final MailService mailService;
    private final SmsService smsService;

    @Value("${app.mail.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw TaskHubException.badRequest("Email already registered");

        LocalDate dateOfBirth = req.getDateOfBirth();
        if (dateOfBirth == null && req.getAge() != null) {
            dateOfBirth = LocalDate.now().minusYears(req.getAge());
        }

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .university(trimToNull(req.getUniversity()))
                .major(trimToNull(req.getMajor()))
                .role(req.getRole())
                .dateOfBirth(dateOfBirth)
                .phone(trimToNull(req.getPhoneNumber() != null ? req.getPhoneNumber() : req.getPhone()))
                .isVerified(false)
                .build();
        user = userRepository.save(user);

        auditService.record("REGISTER", user.getEmail(), "User registered with role: " + user.getRole());
        issueEmailVerificationToken(user);

        return buildAuthResponse(user, generateAndSaveRefreshToken(user.getId()));
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Validate email format
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            auditService.record("LOGIN_FAILURE", req.getEmail(), "Email is blank");
            throw TaskHubException.badRequest("Vui lòng nhập địa chỉ email");
        }

        // Validate password
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            auditService.record("LOGIN_FAILURE", req.getEmail(), "Password is blank");
            throw TaskHubException.badRequest("Vui lòng nhập mật khẩu");
        }

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> {
                    auditService.record("LOGIN_FAILURE", req.getEmail(), "Email not found");
                    return TaskHubException.badRequest("Email hoặc mật khẩu không đúng");
                });

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            auditService.record("LOGIN_FAILURE", user.getEmail(), "Invalid password");
            throw TaskHubException.badRequest("Email hoặc mật khẩu không đúng");
        }

        auditService.record("LOGIN_SUCCESS", user.getEmail(), "User logged in");
        return buildAuthResponse(user, generateAndSaveRefreshToken(user.getId()));
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        String tokenHash = TokenHasher.sha256(req.getRefreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> TaskHubException.unauthorized("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            auditService.record("REFRESH_TOKEN_REVOKED", null, "Attempted to use revoked refresh token");
            throw TaskHubException.unauthorized("Refresh token has been revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            auditService.record("REFRESH_TOKEN_EXPIRED", null, "Attempted to use expired refresh token");
            throw TaskHubException.unauthorized("Refresh token has expired");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String newRefreshToken = generateAndSaveRefreshToken(user.getId());
        auditService.record("REFRESH_TOKEN_SUCCESS", user.getEmail(), "Token refreshed");

        return buildAuthResponse(user, newRefreshToken);
    }

    /**
     * Device-safe logout: revokes only the provided refresh token when present,
     * otherwise revokes all refresh tokens for the user.
     */
    @Transactional
    public void logout(Long userId, LogoutRequest req) {
        if (req != null && req.getRefreshToken() != null && !req.getRefreshToken().isBlank()) {
            String tokenHash = TokenHasher.sha256(req.getRefreshToken());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (token.getUserId().equals(userId)) {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    auditService.record("LOGOUT", null, "Single device logout");
                }
            });
            return;
        }

        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId);
        auditService.record("LOGOUT", null, "User logged out, revoked " + revokedCount + " refresh tokens");
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        userRepository.findByEmail(req.getEmail()).ifPresent(user -> {
            verificationTokenRepository.invalidateActive(
                    user.getId(),
                    VerificationTokenType.PASSWORD_RESET,
                    LocalDateTime.now()
            );

            String rawToken = TokenHasher.randomToken();
            String tokenHash = TokenHasher.sha256(rawToken);

            verificationTokenRepository.save(VerificationToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenHash)
                    .type(VerificationTokenType.PASSWORD_RESET)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build());

            String resetLink = frontendBaseUrl + "/recover?token=" + rawToken;
            mailService.sendPasswordReset(user.getEmail(), resetLink);
            auditService.record("FORGOT_PASSWORD_INITIATED", user.getEmail(), "Password reset email sent");
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> recoverAccount(RecoverAccountRequest req) {
        String channel = req.getChannel() == null ? "EMAIL" : req.getChannel().trim().toUpperCase();
        String contact = req.getContact() == null ? "" : req.getContact().trim();
        var user = "SMS".equals(channel) || "PHONE".equals(channel)
                ? userRepository.findByPhone(contact)
                : userRepository.findByEmail(contact);

        if (user.isEmpty()) {
            return Map.of(
                    "message", "No account found for the provided contact",
                    "found", false
            );
        }

        return Map.of(
                "message", "Account found",
                "found", true,
                "maskedEmail", maskEmail(user.get().getEmail())
        );
    }

    @Transactional
    public Map<String, String> requestPasswordReset(PasswordResetRequest req) {
        String channel = req.getChannel() == null ? "EMAIL" : req.getChannel().trim().toUpperCase();
        String identifier = req.getIdentifier() == null ? "" : req.getIdentifier().trim();
        if ("SMS".equals(channel) || "PHONE".equals(channel)) {
            userRepository.findByPhone(identifier).ifPresent(user -> {
                otpTokenRepository.deleteActiveByPhoneAndType(identifier, "PASSWORD_RESET");
                String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
                otpTokenRepository.save(OtpToken.builder()
                        .phone(identifier)
                        .code(code)
                        .type("PASSWORD_RESET")
                        .expiresAt(LocalDateTime.now().plusMinutes(5))
                        .build());
                smsService.sendOtpRecovery(identifier, code);
                auditService.record("FORGOT_PASSWORD_INITIATED", user.getEmail(), "Password reset SMS sent");
            });
            return Map.of("message", "If the phone number exists, a reset code will be sent");
        }

        forgotPassword(ForgotPasswordRequest.builder().email(identifier).build());
        return Map.of("message", "If the email exists, a reset link will be sent");
    }

    @Transactional
    public Map<String, String> confirmPasswordReset(PasswordResetConfirmRequest req) {
        String identifier = req.getIdentifier() == null ? "" : req.getIdentifier().trim();
        String code = req.getCode() == null ? "" : req.getCode().trim();

        if (!identifier.contains("@")) {
            OtpToken otp = otpTokenRepository.findValidOtp(identifier, "PASSWORD_RESET")
                    .orElseThrow(() -> TaskHubException.badRequest("Invalid or expired reset code"));
            if (!otp.getCode().equals(code)) {
                throw TaskHubException.badRequest("Invalid or expired reset code");
            }

            User user = userRepository.findByPhone(identifier)
                    .orElseThrow(() -> TaskHubException.badRequest("Invalid or expired reset code"));
            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
            userRepository.save(user);
            otp.setUsed(true);
            otpTokenRepository.save(otp);
            refreshTokenRepository.revokeAllByUserId(user.getId());
            auditService.record("PASSWORD_RESET_SUCCESS", user.getEmail(), "Password reset using SMS code");
            return Map.of("message", "Password reset successful");
        }

        resetPassword(ResetPasswordRequest.builder()
                .token(code)
                .newPassword(req.getNewPassword())
                .build());
        return Map.of("message", "Password reset successful");
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String tokenHash = TokenHasher.sha256(req.getToken());

        VerificationToken verificationToken = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> TaskHubException.badRequest("Invalid or expired reset token"));

        if (verificationToken.isUsed()) {
            auditService.record("PASSWORD_RESET_TOKEN_USED", null, "Attempted to use already used token");
            throw TaskHubException.badRequest("Token has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            auditService.record("PASSWORD_RESET_TOKEN_EXPIRED", null, "Attempted to use expired token");
            throw TaskHubException.badRequest("Token has expired");
        }

        if (verificationToken.getType() != VerificationTokenType.PASSWORD_RESET) {
            throw TaskHubException.badRequest("Invalid token type");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        verificationToken.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(verificationToken);
        refreshTokenRepository.revokeAllByUserId(user.getId());

        auditService.record("PASSWORD_RESET_SUCCESS", user.getEmail(), "Password reset using token");
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest req) {
        String tokenHash = TokenHasher.sha256(req.getToken());

        VerificationToken verificationToken = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> TaskHubException.badRequest("Invalid or expired verification token"));

        if (verificationToken.isUsed()) {
            auditService.record("EMAIL_VERIFY_TOKEN_USED", null, "Attempted to use already used token");
            throw TaskHubException.badRequest("Token has already been used");
        }

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            auditService.record("EMAIL_VERIFY_TOKEN_EXPIRED", null, "Attempted to use expired token");
            throw TaskHubException.badRequest("Token has expired");
        }

        if (verificationToken.getType() != VerificationTokenType.EMAIL_VERIFICATION) {
            throw TaskHubException.badRequest("Invalid token type");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(verificationToken);

        auditService.record("EMAIL_VERIFY_SUCCESS", user.getEmail(), "Email verified successfully");
    }

    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    private String generateAndSaveRefreshToken(Long userId) {
        String rawToken = TokenHasher.randomToken();
        String tokenHash = TokenHasher.sha256(rawToken);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpirationMs() / 1000))
                .revoked(false)
                .build());

        return rawToken;
    }

    private void issueEmailVerificationToken(User user) {
        String rawToken = TokenHasher.randomToken();
        String tokenHash = TokenHasher.sha256(rawToken);

        verificationTokenRepository.save(VerificationToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .type(VerificationTokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());

        String verifyLink = frontendBaseUrl + "/verify-email?token=" + rawToken;
        mailService.sendEmailVerification(user.getEmail(), verifyLink);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}

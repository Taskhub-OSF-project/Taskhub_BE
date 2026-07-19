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
import com.taskhub.enums.Role;
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
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
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

    @Value("${app.auth.require-email-verification:false}")
    private boolean requireEmailVerification;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (req.getRole() == Role.ADMIN) {
            throw TaskHubException.forbidden("Admin accounts cannot be self-registered");
        }
        if (userRepository.existsByEmailIgnoreCase(email))
            throw TaskHubException.badRequest("Email already registered");

        String phone = normalizePhone(req.getPhoneNumber() != null ? req.getPhoneNumber() : req.getPhone());
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw TaskHubException.badRequest("Phone number already registered");
        }

        LocalDate dateOfBirth = req.getDateOfBirth();
        if (dateOfBirth == null && req.getAge() != null) {
            dateOfBirth = LocalDate.now().minusYears(req.getAge());
        }
        if (dateOfBirth != null) {
            int age = java.time.Period.between(dateOfBirth, LocalDate.now()).getYears();
            if (dateOfBirth.isAfter(LocalDate.now()) || age < 10 || age > 120) {
                throw TaskHubException.badRequest("Date of birth must represent an age from 10 to 120");
            }
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .university(trimToNull(req.getUniversity()))
                .major(trimToNull(req.getMajor()))
                .role(req.getRole())
                .dateOfBirth(dateOfBirth)
                .phone(phone)
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

        String email = normalizeEmail(req.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    auditService.record("LOGIN_FAILURE", req.getEmail(), "Email not found");
                    return TaskHubException.badRequest("Email hoặc mật khẩu không đúng");
                });

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            auditService.record("LOGIN_FAILURE", user.getEmail(), "Invalid password");
            throw TaskHubException.badRequest("Email hoặc mật khẩu không đúng");
        }
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            auditService.record("LOGIN_FAILURE", user.getEmail(), "Banned account");
            throw TaskHubException.unauthorized("Account is disabled");
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
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            refreshTokenRepository.revokeAllByUserId(user.getId());
            throw TaskHubException.unauthorized("Account is disabled");
        }

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
        userRepository.findByEmailIgnoreCase(normalizeEmail(req.getEmail())).ifPresent(user -> {
            if (!mailService.isDeliveryEnabled()) {
                auditService.record("PASSWORD_RESET_DELIVERY_DISABLED", user.getEmail(),
                        "Password reset provider is disabled");
                return;
            }
            verificationTokenRepository.invalidateActive(
                    user.getId(),
                    VerificationTokenType.PASSWORD_RESET,
                    LocalDateTime.now()
            );

            String rawToken = TokenHasher.randomToken();
            String tokenHash = TokenHasher.sha256(rawToken);

            String resetLink = frontendBaseUrl + "/recover#token=" + rawToken;
            verificationTokenRepository.save(VerificationToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenHash)
                    .type(VerificationTokenType.PASSWORD_RESET)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build());
            try {
                mailService.sendPasswordReset(user.getEmail(), resetLink);
            } catch (RuntimeException ex) {
                auditService.record("PASSWORD_RESET_DELIVERY_FAILURE", user.getEmail(),
                        "Password reset email delivery failed");
                return;
            }
            auditService.record("FORGOT_PASSWORD_INITIATED", user.getEmail(), "Password reset email sent");
        });
    }

    @Transactional
    public Map<String, Object> recoverAccount(RecoverAccountRequest req) {
        String channel = req.getChannel() == null ? "EMAIL" : req.getChannel().trim().toUpperCase();
        String contact = req.getContact() == null ? "" : req.getContact().trim();
        if (!"EMAIL".equals(channel) && !"SMS".equals(channel) && !"PHONE".equals(channel)) {
            throw TaskHubException.badRequest("Unsupported recovery channel");
        }
        if ("SMS".equals(channel) || "PHONE".equals(channel)) {
            requestPasswordReset(PasswordResetRequest.builder()
                    .channel("SMS").identifier(normalizePhone(contact)).build());
        } else {
            forgotPassword(ForgotPasswordRequest.builder().email(normalizeEmail(contact)).build());
        }
        return Map.of("message", "If the account exists, recovery instructions will be sent", "found", true);
    }

    @Transactional
    public Map<String, String> requestPasswordReset(PasswordResetRequest req) {
        String channel = req.getChannel() == null ? "EMAIL" : req.getChannel().trim().toUpperCase();
        String identifier = req.getIdentifier() == null ? "" : req.getIdentifier().trim();
        if (!"EMAIL".equals(channel) && !"SMS".equals(channel) && !"PHONE".equals(channel)) {
            throw TaskHubException.badRequest("Unsupported recovery channel");
        }
        if ("SMS".equals(channel) || "PHONE".equals(channel)) {
            final String phoneIdentifier = normalizePhone(identifier);
            if (!smsService.isDeliveryEnabled()) {
                return Map.of("message", "If the phone number exists, a reset code will be sent");
            }
            userRepository.findByPhone(phoneIdentifier).ifPresent(user -> {
                otpTokenRepository.deleteActiveByPhoneAndType(phoneIdentifier, "PASSWORD_RESET");
                String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
                smsService.sendOtpRecovery(phoneIdentifier, code);
                otpTokenRepository.save(OtpToken.builder()
                        .phone(phoneIdentifier)
                        .code(TokenHasher.sha256(code))
                        .type("PASSWORD_RESET")
                        .expiresAt(LocalDateTime.now().plusMinutes(5))
                        .build());
                auditService.record("FORGOT_PASSWORD_INITIATED", user.getEmail(), "Password reset SMS sent");
            });
            return Map.of("message", "If the phone number exists, a reset code will be sent");
        }

        forgotPassword(ForgotPasswordRequest.builder().email(normalizeEmail(identifier)).build());
        return Map.of("message", "If the email exists, a reset link will be sent");
    }

    @Transactional(noRollbackFor = TaskHubException.class)
    public Map<String, String> confirmPasswordReset(PasswordResetConfirmRequest req) {
        String identifier = req.getIdentifier() == null ? "" : req.getIdentifier().trim();
        String code = req.getCode() == null ? "" : req.getCode().trim();

        if (!identifier.contains("@")) {
            identifier = normalizePhone(identifier);
            OtpToken otp = otpTokenRepository.findValidOtp(identifier, "PASSWORD_RESET")
                    .orElseThrow(() -> TaskHubException.badRequest("Invalid or expired reset code"));
            byte[] expected = otp.getCode().getBytes(StandardCharsets.UTF_8);
            byte[] supplied = TokenHasher.sha256(code).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, supplied)) {
                int attempts = (otp.getFailedAttempts() == null ? 0 : otp.getFailedAttempts()) + 1;
                otp.setFailedAttempts(attempts);
                if (attempts >= 5) otp.setUsed(true);
                otpTokenRepository.save(otp);
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
                .emailVerified(user.isEmailVerified())
                .verificationRequired(requireEmailVerification && !user.isEmailVerified())
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
        if (!mailService.isDeliveryEnabled()) {
            auditService.record("EMAIL_VERIFY_DELIVERY_DISABLED", user.getEmail(),
                    "Email verification provider is disabled");
            return;
        }
        String rawToken = TokenHasher.randomToken();
        String tokenHash = TokenHasher.sha256(rawToken);

        verificationTokenRepository.invalidateActive(
                user.getId(), VerificationTokenType.EMAIL_VERIFICATION, LocalDateTime.now());
        verificationTokenRepository.save(VerificationToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .type(VerificationTokenType.EMAIL_VERIFICATION)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());
        String verifyLink = frontendBaseUrl + "/verify-email#token=" + rawToken;
        try {
            mailService.sendEmailVerification(user.getEmail(), verifyLink);
        } catch (RuntimeException ex) {
            auditService.record("EMAIL_VERIFY_DELIVERY_FAILURE", user.getEmail(),
                    "Email verification delivery failed");
            return;
        }
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizePhone(String value) {
        String phone = trimToNull(value);
        if (phone == null) return null;
        String normalized = phone.replaceAll("[\\s().-]", "");
        if (!normalized.matches("^\\+?[0-9]{8,15}$")) {
            throw TaskHubException.badRequest("Invalid phone number");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}

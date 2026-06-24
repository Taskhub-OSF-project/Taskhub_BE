package com.taskhub.service;

import com.taskhub.dto.request.LoginRequest;
import com.taskhub.dto.request.RefreshTokenRequest;
import com.taskhub.dto.request.RegisterRequest;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.dto.response.ForgotPasswordResponse;
import com.taskhub.entity.OtpToken;
import com.taskhub.entity.PasswordResetToken;
import com.taskhub.entity.RefreshToken;
import com.taskhub.entity.User;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.OtpTokenRepository;
import com.taskhub.repository.PasswordResetTokenRepository;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.JwtService;
import com.taskhub.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-expiration-days:7}")
    private int refreshTokenExpirationDays;

    @Value("${app.jwt.expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.max-per-user:5}")
    private int maxRefreshTokensPerUser;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

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
                .dateOfBirth(req.getDateOfBirth())
                .phone(trimToNull(req.getPhone()))
                .isVerified(false)
                .build();
        user = userRepository.save(user);

        // Send email verification
        String token = jwtService.generateSecureToken();
        String tokenHash = TokenHasher.hash(token);
        PasswordResetToken verificationToken = PasswordResetToken.builder()
                .token(token)
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .used(false)
                .build();
        passwordResetTokenRepository.save(verificationToken);

        String verificationLink = baseUrl + "/verify?token=" + token;
        emailService.sendEmailVerificationEmail(req.getEmail(), req.getFullName(), verificationLink);

        log.info("[AUTH] New registration: userId={}, email={}, role={}", user.getId(), maskEmail(user.getEmail()), user.getRole());
        auditService.logRegistration(user, getRequest());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail()).orElse(null);
        if (user == null) {
            log.warn("[AUTH] Login failed — email not found: {}", maskEmail(req.getEmail()));
            auditService.logLoginFailure(req.getEmail(), "email_not_found", getRequest());
            throw TaskHubException.badRequest("Invalid credentials");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("[AUTH] Login failed — wrong password for email: {}", maskEmail(req.getEmail()));
            auditService.logLoginFailure(req.getEmail(), "wrong_password", getRequest());
            throw TaskHubException.badRequest("Invalid credentials");
        }

        log.info("[AUTH] Login success: userId={}, email={}, role={}", user.getId(), maskEmail(user.getEmail()), user.getRole());
        auditService.logLoginSuccess(user, getRequest());
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
        String newRefreshToken = jwtService.generateRefreshTokenRaw();
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

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        long expiresAt = Instant.now().plusMillis(accessTokenExpirationMs).getEpochSecond();

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest req) {
        String providedHash = TokenHasher.hash(req.getRefreshToken());
        int revoked = refreshTokenRepository.revokeByTokenHash(providedHash);
        if (revoked > 0) {
            log.info("[AUTH] User logged out: refresh token revoked");
        }
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.revokeAllUserTokens(userId, "logout-all-" + userId);
        log.info("[AUTH] User logged out from all devices: userId={}", userId);
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(String email) {
        var optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            log.warn("[AUTH] Password reset requested for unknown email: {}", maskEmail(email));
            auditService.logPasswordResetRequest(email, false, getRequest());
            // Always return generic response to prevent email enumeration
            return ForgotPasswordResponse.builder()
                    .emailSent(false)
                    .build();
        }

        User user = optUser.get();
        String token = jwtService.generateSecureToken();
        String tokenHash = TokenHasher.hash(token);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .createdAt(LocalDateTime.now())
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        String resetLink = baseUrl + "/recover?token=" + token + "&flow=password";
        emailService.sendPasswordResetEmail(email, user.getFullName(), resetLink);
        // Email is sent asynchronously — flag reflects SMTP config, not delivery guarantee.
        boolean smtpEnabled = emailService.isEmailEnabled();
        log.info("[AUTH] Password reset requested: userId={}, email={}, smtpEnabled={}",
                user.getId(), maskEmail(email), smtpEnabled);
        auditService.logPasswordResetRequest(email, true, getRequest());

        // When SMTP is disabled, include the reset link and token so the user can still
        // complete the password reset flow during local development. In production with
        // SMTP enabled, only `emailSent=true` is returned to preserve email-enumeration safety.
        if (smtpEnabled) {
            return ForgotPasswordResponse.builder().emailSent(true).build();
        }
        return ForgotPasswordResponse.builder()
                .emailSent(false)
                .resetLink(resetLink)
                .token(token)
                .build();
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String tokenHash = TokenHasher.hash(token);
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> TaskHubException.badRequest("Invalid or expired reset token"));

        if (!stored.isValid()) {
            log.warn("[AUTH] Password reset failed — token invalid/expired");
            throw TaskHubException.badRequest("Reset token has expired or already been used");
        }

        User user = stored.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        stored.setUsed(true);
        passwordResetTokenRepository.save(stored);
        refreshTokenRepository.revokeAllUserTokens(user.getId(), "reset-" + user.getId());
        log.info("[AUTH] Password reset successful: userId={}", user.getId());
        auditService.logPasswordResetSuccess(user, getRequest());
    }

    @Transactional
    public void verifyEmail(String token) {
        String tokenHash = TokenHasher.hash(token);
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> TaskHubException.badRequest("Invalid or expired verification token"));

        if (!stored.isValid()) {
            log.warn("[AUTH] Email verification failed — token invalid/expired");
            throw TaskHubException.badRequest("Verification token has expired or already been used");
        }

        User user = stored.getUser();
        user.setIsVerified(true);
        userRepository.save(user);

        stored.setUsed(true);
        passwordResetTokenRepository.save(stored);
        log.info("[AUTH] Email verified: userId={}, email={}", user.getId(), maskEmail(user.getEmail()));
        auditService.logSecurityEvent(AuditService.SecurityEventParams.builder()
                .user(user).userEmailHash(sha256prefix(user.getEmail()))
                .eventType(com.taskhub.entity.SecurityEvent.EventType.EMAIL_VERIFICATION)
                .outcome("SUCCESS")
                .build());
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("[AUTH] Password change failed — wrong current password: userId={}", userId);
            throw TaskHubException.badRequest("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(userId, "password-change-" + userId);
        log.info("[AUTH] Password changed successfully: userId={}", userId);
        auditService.logSecurityEvent(AuditService.SecurityEventParams.builder()
                .user(user).userEmailHash(sha256prefix(user.getEmail()))
                .eventType(com.taskhub.entity.SecurityEvent.EventType.PASSWORD_CHANGE)
                .outcome("SUCCESS")
                .build());
    }

    // ── Phone Auth ───────────────────────────────────────────────

    @Transactional
    public AuthResponse loginByPhone(String phone, String password) {
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            log.warn("[AUTH] Login by phone failed — phone not found: {}", maskPhone(phone));
            auditService.logLoginFailure(phone, "phone_not_found", getRequest());
            throw TaskHubException.badRequest("Invalid credentials");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("[AUTH] Login by phone failed — wrong password for phone: {}", maskPhone(phone));
            auditService.logLoginFailure(phone, "wrong_password", getRequest());
            throw TaskHubException.badRequest("Invalid credentials");
        }
        log.info("[AUTH] Phone login success: userId={}, phone={}", user.getId(), maskPhone(phone));
        auditService.logLoginSuccess(user, getRequest());
        return buildAuthResponse(user);
    }

    @Transactional
    public void requestPhoneOtp(String phone, String type) {
        if ("REGISTRATION".equals(type) && userRepository.existsByPhone(phone)) {
            throw TaskHubException.badRequest("Phone number already registered");
        }
        // Delete any existing unused OTPs for this phone+type
        otpTokenRepository.deleteActiveByPhoneAndType(phone, type);
        String code = generateOtp();
        OtpToken otp = OtpToken.builder()
                .phone(phone)
                .code(code)
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();
        otpTokenRepository.save(otp);
        smsService.sendOtp(phone, code);
        log.info("[AUTH] OTP requested: phone={}, type={}, smtpEnabled={}", maskPhone(phone), type, smsService != null);
    }

    @Transactional
    public AuthResponse verifyPhoneOtpAndRegister(String phone, String code, RegisterRequest registerReq) {
        String maskedPhone = maskPhone(phone);
        OtpToken otp = otpTokenRepository.findValidOtp(phone, "REGISTRATION")
                .orElseThrow(() -> {
                    log.warn("[AUTH] OTP verification failed — no valid token for phone: {}", maskedPhone);
                    return TaskHubException.badRequest("Invalid or expired OTP");
                });

        if (!otp.getCode().equals(code)) {
            log.warn("[AUTH] OTP verification failed — wrong code for phone: {}", maskedPhone);
            throw TaskHubException.badRequest("Invalid or expired OTP");
        }

        if (userRepository.existsByEmail(registerReq.getEmail())) {
            throw TaskHubException.badRequest("Email already registered");
        }

        otp.setUsed(true);
        otpTokenRepository.save(otp);

        User user = User.builder()
                .email(registerReq.getEmail())
                .password(passwordEncoder.encode(registerReq.getPassword()))
                .fullName(registerReq.getFullName())
                .university(trimToNull(registerReq.getUniversity()))
                .major(trimToNull(registerReq.getMajor()))
                .role(registerReq.getRole())
                .dateOfBirth(registerReq.getDateOfBirth())
                .phone(phone)
                .isVerified(true)
                .build();
        user = userRepository.save(user);

        log.info("[AUTH] Phone-OTP registration success: userId={}, phone={}", user.getId(), maskedPhone);
        auditService.logRegistration(user, getRequest());
        return buildAuthResponse(user);
    }

    @Transactional
    public ForgotPasswordResponse forgotPasswordByPhone(String phone) {
        var optUser = userRepository.findByPhone(phone);
        if (optUser.isEmpty()) {
            log.warn("[AUTH] Password reset by phone requested for unknown phone: {}", maskPhone(phone));
            auditService.logPasswordResetRequest(phone, false, getRequest());
            return ForgotPasswordResponse.builder().emailSent(false).build();
        }

        User user = optUser.get();
        String code = generateOtp();
        OtpToken otp = OtpToken.builder()
                .phone(phone)
                .code(code)
                .type("RECOVERY")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();
        otpTokenRepository.save(otp);
        smsService.sendOtpRecovery(phone, code);

        log.info("[AUTH] Password reset by phone: userId={}, phone={}", user.getId(), maskPhone(phone));
        auditService.logPasswordResetRequest(phone, true, getRequest());
        return ForgotPasswordResponse.builder().emailSent(true).build();
    }

    @Transactional
    public void resetPasswordWithOtp(String phone, String code, String newPassword) {
        String maskedPhone = maskPhone(phone);
        OtpToken otp = otpTokenRepository.findValidOtp(phone, "RECOVERY")
                .orElseThrow(() -> {
                    log.warn("[AUTH] Password reset with OTP failed — no valid token for phone: {}", maskedPhone);
                    return TaskHubException.badRequest("Invalid or expired OTP");
                });

        if (!otp.getCode().equals(code)) {
            log.warn("[AUTH] Password reset with OTP failed — wrong code for phone: {}", maskedPhone);
            throw TaskHubException.badRequest("Invalid or expired OTP");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        otp.setUsed(true);
        otpTokenRepository.save(otp);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user.getId(), "otp-reset-" + user.getId());
        log.info("[AUTH] Password reset with OTP successful: userId={}, phone={}", user.getId(), maskedPhone);
        auditService.logPasswordResetSuccess(user, getRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String generateOtp() {
        int code = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, phone.length() - 4).replaceAll("[0-9]", "*") + phone.substring(phone.length() - 4);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshTokenRaw();
        long expiresAt = Instant.now().plusMillis(accessTokenExpirationMs).getEpochSecond();

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
                .expiresAt(expiresAt)
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

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts.length > 1 ? parts[1] : "";
        String maskedLocal = local.length() > 2
                ? local.substring(0, 2) + "***"
                : "***";
        return maskedLocal + "@" + domain;
    }

    private String sha256prefix(String value) {
        if (value == null) return null;
        try {
            return java.util.Base64.getEncoder().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.trim().toLowerCase().getBytes())
            ).substring(0, 16);
        } catch (Exception e) {
            return "***";
        }
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

package com.taskhub.controller;

import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import com.taskhub.exception.TaskHubException;
import com.taskhub.security.RefreshTokenCookieService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookies;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req,
                                                               HttpServletResponse response) {
        AuthResponse auth = prepareAuthResponse(response, authService.register(req));
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", auth));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req,
                                                            HttpServletResponse response) {
        AuthResponse auth = prepareAuthResponse(response, authService.login(req));
        return ResponseEntity.ok(ApiResponse.ok("Login successful", auth));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
            @CookieValue(name = "taskhub_refresh", required = false) String cookieToken,
            HttpServletResponse response) {
        requireSpaRequest(requestedWith);
        if (cookieToken == null || cookieToken.isBlank()) {
            throw TaskHubException.unauthorized("Refresh token cookie is required");
        }
        AuthResponse auth = authService.refreshToken(
                RefreshTokenRequest.builder().refreshToken(cookieToken).build());
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed",
                refreshTokenCookies.moveRefreshTokenToCookie(response, auth)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
            @CookieValue(name = "taskhub_refresh", required = false) String cookieToken,
            HttpServletResponse response) {
        requireSpaRequest(requestedWith);
        LogoutRequest req = cookieToken == null || cookieToken.isBlank()
                ? null
                : LogoutRequest.builder().refreshToken(cookieToken).build();
        authService.logout(AuthUtil.getCurrentUser().getId(), req);
        refreshTokenCookies.clear(response);
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(ApiResponse.ok(
                "If the email exists, a reset link will be sent", null));
    }

    @PostMapping("/recover-account")
    public ResponseEntity<ApiResponse<Object>> recoverAccount(@Valid @RequestBody RecoverAccountRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Account recovery checked",
                authService.recoverAccount(req)));
    }

    @PostMapping("/recover-password/request")
    public ResponseEntity<ApiResponse<Object>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Password reset requested",
                authService.requestPasswordReset(req)));
    }

    @PostMapping("/recover-password/confirm")
    public ResponseEntity<ApiResponse<Object>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Password reset confirmed",
                authService.confirmPasswordReset(req)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful", null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req);
        return ResponseEntity.ok(ApiResponse.ok("Email verified", null));
    }

    @PostMapping("/email-otp/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmailOtp(
            @Valid @RequestBody EmailOtpVerifyRequest req,
            HttpServletResponse response) {
        AuthResponse auth = authService.verifyEmailOtp(req);
        if (auth.getToken() != null) {
            auth = refreshTokenCookies.moveRefreshTokenToCookie(response, auth);
        }
        return ResponseEntity.ok(ApiResponse.ok("OTP verified", auth));
    }

    @PostMapping("/email-otp/resend")
    public ResponseEntity<ApiResponse<Void>> resendEmailOtp(
            @Valid @RequestBody EmailOtpResendRequest req) {
        authService.resendEmailOtp(req);
        return ResponseEntity.ok(ApiResponse.ok("A new OTP has been sent", null));
    }

    private AuthResponse prepareAuthResponse(HttpServletResponse response, AuthResponse auth) {
        if (auth.isVerificationRequired() || auth.isEmailOtpRequired()) {
            auth.setToken(null);
            auth.setRefreshToken(null);
            return auth;
        }
        return refreshTokenCookies.moveRefreshTokenToCookie(response, auth);
    }

    private void requireSpaRequest(String requestedWith) {
        if (!"XMLHttpRequest".equals(requestedWith)) {
            throw TaskHubException.unauthorized("Missing CSRF request header");
        }
    }
}

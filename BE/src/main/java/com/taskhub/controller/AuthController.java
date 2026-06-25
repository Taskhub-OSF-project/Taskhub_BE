package com.taskhub.controller;

import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", authService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", authService.login(req)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", authService.refreshToken(req)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequest req) {
        authService.logout(AuthUtil.getCurrentUser().getId(), req);
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
}

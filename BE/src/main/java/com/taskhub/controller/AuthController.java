package com.taskhub.controller;

import com.taskhub.dto.request.ForgotPasswordRequest;
import com.taskhub.dto.request.LogoutRequest;
import com.taskhub.dto.request.RefreshTokenRequest;
import com.taskhub.dto.request.RegisterRequest;
import com.taskhub.dto.request.ResetPasswordRequest;
import com.taskhub.dto.request.VerifyEmailRequest;
import com.taskhub.dto.request.LoginRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.dto.response.ForgotPasswordResponse;
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
        if (req != null && req.getRefreshToken() != null) {
            authService.logout(RefreshTokenRequest.builder().refreshToken(req.getRefreshToken()).build());
        }
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll() {
        authService.logoutAll(AuthUtil.getCurrentUser().getId());
        return ResponseEntity.ok(ApiResponse.ok("Logged out from all devices", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        // Always return generic message to prevent email enumeration
        ForgotPasswordResponse data = authService.forgotPassword(req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(
                "If an account with that email exists, a reset link has been sent", data));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful. Please login with your new password.", null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req.getToken());
        return ResponseEntity.ok(ApiResponse.ok("Email verified successfully", null));
    }
}

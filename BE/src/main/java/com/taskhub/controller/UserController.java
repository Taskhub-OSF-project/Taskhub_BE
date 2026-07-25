package com.taskhub.controller;

import com.taskhub.dto.request.ChangePasswordRequest;
import com.taskhub.dto.request.UpdateProfileRequest;
import com.taskhub.dto.request.SwitchRoleRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import com.taskhub.security.RefreshTokenCookieService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final RefreshTokenCookieService refreshTokenCookies;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        Long userId = AuthUtil.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(id)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest req) {
        Long userId = AuthUtil.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.updateProfile(userId, req)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> replaceProfile(
            @Valid @RequestBody UpdateProfileRequest req) {
        Long userId = AuthUtil.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.updateProfile(userId, req)));
    }

    @PostMapping("/me/availability")
    public ResponseEntity<ApiResponse<UserProfileResponse>> setAvailability(
            @RequestParam(defaultValue = "true") boolean available) {
        Long userId = AuthUtil.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.ok("Availability updated", userService.setAvailability(userId, available)));
    }

    @PostMapping("/switch-role")
    public ResponseEntity<ApiResponse<com.taskhub.dto.response.AuthResponse>> switchRole(
            @Valid @RequestBody SwitchRoleRequest req,
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
            HttpServletResponse response) {
        Long userId = AuthUtil.getCurrentUser().getId();
        var auth = refreshTokenCookies.processRefreshToken(
                response, userService.switchRoleAndReturnToken(userId, req.getRole()), requestedWith);
        return ResponseEntity.ok(ApiResponse.ok("Role switched", auth));
    }

    @PatchMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        Long userId = AuthUtil.getCurrentUser().getId();
        userService.changePassword(userId, req);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }
}

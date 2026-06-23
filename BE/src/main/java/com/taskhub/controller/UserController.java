package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.request.UserProfileUpdateRequest;
import com.taskhub.dto.request.ChangePasswordRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.AuthService;
import com.taskhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.ok("Profile retrieved", userService.getMyProfile()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Profile retrieved", userService.getProfile(id)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.updateMyProfile(req)));
    }

    @PostMapping("/me/availability")
    public ResponseEntity<ApiResponse<Void>> setAvailability(@RequestParam boolean available) {
        userService.setAvailability(available);
        return ResponseEntity.ok(ApiResponse.ok("Availability updated", null));
    }

    @PatchMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(AuthUtil.getCurrentUser().getId(), req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }
}

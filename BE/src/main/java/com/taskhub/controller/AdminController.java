package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.BroadcastNotificationRequest;
import com.taskhub.dto.request.DisputeResolveRequest;
import com.taskhub.dto.response.AdminDashboardResponse;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.DisputeResolveResponse;
import com.taskhub.dto.response.TaskResponse;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.enums.Role;
import com.taskhub.service.DisputeService;
import com.taskhub.service.NotificationService;
import com.taskhub.service.TaskService;
import com.taskhub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService userService;
    private final TaskService taskService;
    private final DisputeService disputeService;
    private final NotificationService notificationService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok("Dashboard data retrieved",
                userService.getAdminDashboardStats()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        if (role != null && !role.isBlank()) {
            Role userRole = Role.valueOf(role.toUpperCase());
            return ResponseEntity.ok(ApiResponse.ok("Users retrieved",
                    userService.getUsersByRole(userRole, pageReq)));
        }
        return ResponseEntity.ok(ApiResponse.ok("Users retrieved",
                userService.getAllUsers(pageReq)));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("User retrieved",
                userService.getProfile(id)));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserProfileResponse>> changeUserRole(
            @PathVariable Long id, @RequestParam String role) {
        UserProfileResponse updated = userService.changeUserRole(id, Role.valueOf(role.toUpperCase()));
        return ResponseEntity.ok(ApiResponse.ok("User role updated", updated));
    }

    @PostMapping("/users/{id}/ban")
    public ResponseEntity<ApiResponse<Void>> banUser(@PathVariable Long id) {
        userService.setUserBanned(id, true);
        return ResponseEntity.ok(ApiResponse.ok("User banned", null));
    }

    @PostMapping("/users/{id}/unban")
    public ResponseEntity<ApiResponse<Void>> unbanUser(@PathVariable Long id) {
        userService.setUserBanned(id, false);
        return ResponseEntity.ok(ApiResponse.ok("User unbanned", null));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> getTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).sortBy(sortBy).sortDir(sortDir).build();
        return ResponseEntity.ok(ApiResponse.ok("Tasks retrieved",
                taskService.getAllTasks(status, pageReq)));
    }

    @PostMapping("/tasks/{taskId}/dispute/resolve")
    public ResponseEntity<ApiResponse<DisputeResolveResponse>> resolveDispute(
            @PathVariable Long taskId,
            @Valid @RequestBody DisputeResolveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Dispute resolved by admin",
                disputeService.adminResolveDispute(taskId, req)));
    }

    @GetMapping("/disputes/escalated")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> getEscalatedDisputes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).sortBy("createdAt").sortDir("desc").build();
        return ResponseEntity.ok(ApiResponse.ok("Escalated disputes retrieved",
                taskService.getEscalatedDisputes(pageReq)));
    }

    @PostMapping("/notifications/broadcast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> broadcastNotification(
            @Valid @RequestBody BroadcastNotificationRequest req) {
        int sent = notificationService.broadcast(req);
        return ResponseEntity.ok(ApiResponse.ok("Broadcast sent",
                Map.of("recipients", sent,
                        "targetRole", req.getTargetRole() == null ? "ALL" : req.getTargetRole())));
    }
}

package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.response.AdminDashboardResponse;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.TaskResponse;
import com.taskhub.dto.response.UserProfileResponse;
import com.taskhub.enums.Role;
import com.taskhub.service.TaskService;
import com.taskhub.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final TaskService taskService;

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
}

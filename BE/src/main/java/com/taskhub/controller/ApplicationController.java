package com.taskhub.controller;

import com.taskhub.dto.request.ApplicationRequest;
import com.taskhub.dto.response.*;
import com.taskhub.service.ApplicationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Slf4j
public class ApplicationController {
    private final ApplicationService applicationService;
    // Remove unused TaskService and AiValidationService

    @PostConstruct
    public void printSwaggerUrl() {
        log.info("Swagger UI: http://localhost:8080/swagger-ui.html");
    }

    /**
     * Trang redirect nhanh sang Swagger (phục vụ dev).
     */
    }

    /**
     * Student apply vào một task đang ACTIVE.
     * Input: ApplicationRequest (coverLetter tùy chọn).
     * Output: ApplicationResponse.
     */
        return ResponseEntity.ok(ApiResponse.ok("Applied successfully", applicationService.apply(taskId, req)));
    }

    /**
     * Hirer chấp nhận một đơn ứng tuyển.
     * Business rule: task chuyển IN_PROGRESS, các đơn khác bị REJECTED.
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<Void>> accept(@PathVariable Long id) {
        applicationService.acceptApplication(id);
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> taskApps(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getTaskApplications(taskId)));
    }

    /**
     * Danh sách đơn ứng tuyển của student hiện tại.
     */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> myApps() {
     */
    @GetMapping("/my-applied-tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> myAppliedTasks() {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMyAppliedTasks()));
    }
}
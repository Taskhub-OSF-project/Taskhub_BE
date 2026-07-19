package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.ApplicationRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.ApplicationResponse;
import com.taskhub.dto.response.TaskResponse;
import com.taskhub.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService applicationService;

    @PostMapping("/task/{taskId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @PathVariable Long taskId,
            @RequestBody ApplicationRequest req) {
        return ResponseEntity.ok(
                ApiResponse.ok("Applied successfully", applicationService.apply(taskId, req)));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('HIRER')")
    public ResponseEntity<ApiResponse<Void>> accept(@PathVariable Long id) {
        applicationService.acceptApplication(id);
        return ResponseEntity.ok(ApiResponse.ok("Application accepted", null));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('HIRER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ApplicationResponse>>> taskApps(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getTaskApplications(taskId, pageReq)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<ApplicationResponse>>> myApps(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequestDto pageReq = PageRequestDto.builder().page(page).size(size).build();
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMyApplications(pageReq)));
    }

    @GetMapping("/my-applied-tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> myAppliedTasks() {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMyAppliedTasks()));
    }
}

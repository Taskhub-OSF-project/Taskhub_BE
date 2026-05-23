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

    @GetMapping("/")
    public String home() {
        return "redirect:/swagger-ui.html";
    }

    @PostMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(@PathVariable Long taskId, @RequestBody ApplicationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Applied successfully", applicationService.apply(taskId, req)));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<Void>> accept(@PathVariable Long id) {
        applicationService.acceptApplication(id);
        return ResponseEntity.ok(ApiResponse.ok("Application accepted", null));
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> taskApps(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getTaskApplications(taskId)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> myApps() {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMyApplications()));
    }
}
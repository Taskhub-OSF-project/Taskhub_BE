package com.taskhub.controller;

import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Task;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.service.AiValidationService;
import com.taskhub.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    private final AiValidationService aiValidationService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody CreateTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Task created", taskService.createTask(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTask(id)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> myTasks() {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getMyTasks()));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> available() {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getAvailableTasks()));
    }

    // AI validation endpoint (preview only - doesn't lock)
    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<AiValidationService.ValidationResult>> validateCriteria(@PathVariable UUID id) {
        Task task = taskService.findOwnedTask(id);
        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription).toList();

        var result = aiValidationService.validateCriteriaEnhanced(criteria);
        return ResponseEntity.ok(ApiResponse.ok("Validation completed", result));
    }

    // SINGLE lock endpoint with enhanced validation flow
    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<ValidationPhaseResponse>> lockWithValidation(@PathVariable UUID id) {
        try {
            // Simulate AI processing delay (1.8s as per prototype requirement)
            Thread.sleep(1800);

            Task task = taskService.findOwnedTask(id);
            List<String> criteria = task.getAcceptanceCriteria().stream()
                    .map(AcceptanceCriteria::getDescription).toList();

            // Enhanced AI validation first
            var result = aiValidationService.validateCriteriaEnhanced(criteria);

            if (!result.valid()) {
                // Return validation failed state (block UI)
                return ResponseEntity.ok(ApiResponse.ok("Validation failed",
                        ValidationPhaseResponse.builder()
                                .validationPhase("failed")
                                .blockReason(result.message())
                                .canProceed(false)
                                .suggestions(generateSuggestions(criteria))
                                .build()));
            }

            // If validation passes → lock task
            TaskResponse lockedTask = taskService.lockTask(id);
            return ResponseEntity.ok(ApiResponse.ok("Task locked successfully",
                    ValidationPhaseResponse.builder()
                            .validationPhase("passed")
                            .canProceed(true)
                            .message("All criteria meet standards")
                            .taskResponse(lockedTask)
                            .build()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw TaskHubException.internalError("Validation interrupted");
        }
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<TaskResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Task completed", taskService.transitionTask(id, TaskStatus.COMPLETED)));
    }

    @PostMapping("/{id}/revision")
    public ResponseEntity<ApiResponse<TaskResponse>> revision(@PathVariable UUID id, @Valid @RequestBody RevisionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Revision requested", taskService.requestRevision(id, req)));
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<ApiResponse<TaskResponse>> dispute(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Task disputed", taskService.disputeTask(id)));
    }

    // Helper method to generate suggestions
    private List<AiValidationService.CriteriaSuggestion> generateSuggestions(List<String> criteria) {
        return criteria.stream()
                .map(c -> {
                    if (c.length() < 12) {
                        return new AiValidationService.CriteriaSuggestion("Too short",
                                c + " with PNG format, minimum size 1920x1080px");
                    }
                    return new AiValidationService.CriteriaSuggestion("", c);
                })
                .toList();
    }
}
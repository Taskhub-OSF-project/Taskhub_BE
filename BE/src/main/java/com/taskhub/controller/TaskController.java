package com.taskhub.controller;

import com.taskhub.dto.request.*;
import com.taskhub.dto.response.*;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.dto.request.DisputeRequest;
import com.taskhub.dto.request.DisputeResolveRequest;
import com.taskhub.dto.response.DisputeAIReport;
import com.taskhub.dto.response.DisputeResolveResponse;
import com.taskhub.service.AiValidationService;
import com.taskhub.service.CriteriaExtractionService;
import com.taskhub.service.DisputeService;
import com.taskhub.service.SubmissionService;
import com.taskhub.service.TaskService;
import com.taskhub.enums.TaskStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;
    private final AiValidationService aiValidationService;
    private final CriteriaExtractionService criteriaExtractionService;
    private final SubmissionService submissionService;
    private final DisputeService disputeService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody CreateTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Task created", taskService.createTask(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTask(id)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> myTasks(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getMyTasks(status)));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> available() {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getAvailableTasks()));
    }

    /** Lint criteria while drafting (no task id required). */
    @PostMapping("/validate-criteria")
    public ResponseEntity<ApiResponse<AiValidationService.ValidationResult>> validateCriteriaDraft(
            @Valid @RequestBody ValidateCriteriaRequest req) {
        var result = taskService.validateCriteriaList(req.getAcceptanceCriteria());
        return ResponseEntity.ok(ApiResponse.ok("Validation completed", result));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<AiValidationService.ValidationResult>> validateCriteria(@PathVariable Long id) {
        var task = taskService.findOwnedTask(id);
        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription).toList();
        return ResponseEntity.ok(ApiResponse.ok("Validation completed",
                taskService.validateCriteriaList(criteria)));
    }

    /** AI-assisted criteria from uploaded brief (PDF, Excel, image, DOCX). */
    @PostMapping(value = "/criteria/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CriteriaExtractResponse>> extractCriteria(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Criteria extracted",
                criteriaExtractionService.extractFromFile(file)));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<ValidationPhaseResponse>> lockWithValidation(@PathVariable Long id) {
        var task = taskService.findOwnedTask(id);
        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription).toList();

        var result = aiValidationService.validateCriteriaEnhanced(criteria);

        if (!result.valid()) {
            return ResponseEntity.ok(ApiResponse.ok("Validation failed",
                    ValidationPhaseResponse.builder()
                            .validationPhase("failed")
                            .blockReason(result.message())
                            .canProceed(false)
                            .details(result.details())
                            .suggestions(toSuggestions(result.details()))
                            .build()));
        }

        TaskResponse lockedTask = taskService.lockTask(id);
        return ResponseEntity.ok(ApiResponse.ok("Task locked successfully",
                ValidationPhaseResponse.builder()
                        .validationPhase("passed")
                        .canProceed(true)
                        .message("All criteria meet standards")
                        .details(result.details())
                        .taskResponse(lockedTask)
                        .build()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<TaskResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Task completed",
                taskService.transitionTask(id, TaskStatus.COMPLETED)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<TaskResponse>> publish(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Task published", taskService.publishTask(id)));
    }

    @PostMapping("/{id}/revision")
    public ResponseEntity<ApiResponse<RevisionRequestResponse>> revision(
            @PathVariable Long id, @Valid @RequestBody RevisionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Revision requested", submissionService.requestRevision(id, req)));
    }

    /**
     * POST /api/tasks/{id}/dispute
     * Hirer mở dispute khi task SUBMITTED. Sinh AI report và lưu vào task.
     */
    @PostMapping("/{id}/dispute")
    public ResponseEntity<ApiResponse<DisputeAIReport>> dispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Dispute opened", disputeService.openDispute(id, req)));
    }

    /**
     * GET /api/tasks/{id}/dispute/report
     * Hirer hoặc student xem báo cáo AI cho dispute.
     */
    @GetMapping("/{id}/dispute/report")
    public ResponseEntity<ApiResponse<DisputeAIReport>> getDisputeReport(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.getDisputeReport(id)));
    }

    /**
     * POST /api/tasks/{id}/dispute/resolve
     * Hirer giải quyết dispute: RELEASE_PAYMENT | REQUEST_REVISION | ESCALATE.
     */
    @PostMapping("/{id}/dispute/resolve")
    public ResponseEntity<ApiResponse<DisputeResolveResponse>> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeResolveRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Dispute resolved", disputeService.resolveDispute(id, req)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> patch(
            @PathVariable Long id, @Valid @RequestBody PatchTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Task updated", taskService.updateTask(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok(ApiResponse.ok("Task deleted", null));
    }

    private List<AiValidationService.CriteriaSuggestion> toSuggestions(
            List<com.taskhub.entity.CriteriaValidationDetail> details) {
        if (details == null) return List.of();
        return details.stream()
                .filter(d -> !d.isValid())
                .map(d -> new AiValidationService.CriteriaSuggestion(
                        d.getIssue() != null ? d.getIssue() : "Invalid",
                        d.getSuggestion() != null ? d.getSuggestion() : d.getCriteria()))
                .toList();
    }
}

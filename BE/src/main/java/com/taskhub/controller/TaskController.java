package com.taskhub.controller;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
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
import com.taskhub.service.EscrowService;
import com.taskhub.service.SubmissionService;
import com.taskhub.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final EscrowService escrowService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HIRER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody CreateTaskRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Task created", taskService.createTask(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getTask(id)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> myTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PageRequestDto pageReq = PageRequestDto.builder()
                .page(page).size(size).sortBy(sortBy).sortDir(sortDir).build();
        return ResponseEntity.ok(ApiResponse.ok(taskService.getMyTasks(status, pageReq)));
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> available(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PageRequestDto pageReq = PageRequestDto.builder()
                .page(page).size(size).sortBy(sortBy).sortDir(sortDir).build();
        return ResponseEntity.ok(ApiResponse.ok(taskService.getAvailableTasks(pageReq)));
    }

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
        escrowService.releaseEscrow(id);
        return ResponseEntity.ok(ApiResponse.ok("Task completed", taskService.getTask(id)));
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

    @PostMapping("/{id}/dispute")
    public ResponseEntity<ApiResponse<DisputeAIReport>> dispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Dispute opened", disputeService.openDispute(id, req)));
    }

    @GetMapping("/{id}/dispute/report")
    public ResponseEntity<ApiResponse<DisputeAIReport>> getDisputeReport(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.getDisputeReport(id)));
    }

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

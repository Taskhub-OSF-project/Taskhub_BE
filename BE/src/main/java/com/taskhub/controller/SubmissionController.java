package com.taskhub.controller;

import com.taskhub.dto.request.RevisionRequest;
import com.taskhub.dto.request.SubmissionRequest;
import com.taskhub.dto.response.*;
import com.taskhub.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {
    private final SubmissionService submissionService;

    @PostMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<SubmissionResponse>> submit(@PathVariable Long taskId, @Valid @RequestBody SubmissionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Submitted", submissionService.submit(taskId, req)));
    }

    @PostMapping("/task/{taskId}/precheck")
    public ResponseEntity<ApiResponse<SubmissionAIResult>> precheck(@PathVariable Long taskId, @Valid @RequestBody SubmissionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Precheck evaluated", submissionService.precheck(taskId, req)));
    }

    @PostMapping("/task/{taskId}/revision")
    public ResponseEntity<ApiResponse<RevisionRequestResponse>> requestRevision(
            @PathVariable Long taskId, @Valid @RequestBody RevisionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Revision requested", submissionService.requestRevision(taskId, req)));
    }

    @GetMapping("/task/{taskId}/revisions")
    public ResponseEntity<ApiResponse<List<RevisionRequestResponse>>> revisions(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(submissionService.getRevisionHistory(taskId)));
    }

    @PostMapping("/task/{taskId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long taskId) {
        submissionService.approveSubmission(taskId);
        return ResponseEntity.ok(ApiResponse.ok("Submission approved", null));
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<List<SubmissionResponse>>> taskSubs(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(submissionService.getTaskSubmissions(taskId)));
    }

    @GetMapping("/task/{taskId}/latest")
    public ResponseEntity<ApiResponse<LatestSubmissionResultResponse>> latest(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(submissionService.getLatest(taskId)));
    }

    @GetMapping("/task/{taskId}/dispute-report")
    public ResponseEntity<ApiResponse<String>> disputeReport(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(submissionService.generateDisputeReport(taskId)));
    }
}

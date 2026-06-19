package com.taskhub.controller;

import com.taskhub.dto.request.CreateMilestoneRequest;
import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.MilestoneResponse;
import com.taskhub.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {
    private final MilestoneService milestoneService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MilestoneResponse>>> getMilestones(@PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.ok("Milestones retrieved",
                milestoneService.getMilestonesByTask(taskId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MilestoneResponse>> createMilestone(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateMilestoneRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Milestone created",
                milestoneService.createMilestone(taskId, req)));
    }

    @PutMapping("/{milestoneId}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> updateMilestone(
            @PathVariable Long taskId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody CreateMilestoneRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Milestone updated",
                milestoneService.updateMilestone(taskId, milestoneId, req)));
    }

    @DeleteMapping("/{milestoneId}")
    public ResponseEntity<ApiResponse<Void>> deleteMilestone(
            @PathVariable Long taskId,
            @PathVariable Long milestoneId) {
        milestoneService.deleteMilestone(taskId, milestoneId);
        return ResponseEntity.ok(ApiResponse.ok("Milestone deleted", null));
    }

    @PostMapping("/{milestoneId}/fund")
    public ResponseEntity<ApiResponse<MilestoneResponse>> fundMilestone(
            @PathVariable Long taskId,
            @PathVariable Long milestoneId) {
        return ResponseEntity.ok(ApiResponse.ok("Milestone funded",
                milestoneService.fundMilestone(taskId, milestoneId)));
    }

    @PostMapping("/{milestoneId}/approve")
    public ResponseEntity<ApiResponse<MilestoneResponse>> approveMilestone(
            @PathVariable Long taskId,
            @PathVariable Long milestoneId) {
        return ResponseEntity.ok(ApiResponse.ok("Milestone approved and payment released",
                milestoneService.approveMilestone(taskId, milestoneId)));
    }

    @PostMapping("/{milestoneId}/reject")
    public ResponseEntity<ApiResponse<MilestoneResponse>> rejectMilestone(
            @PathVariable Long taskId,
            @PathVariable Long milestoneId) {
        return ResponseEntity.ok(ApiResponse.ok("Milestone rejected and payment refunded",
                milestoneService.rejectMilestone(taskId, milestoneId)));
    }
}

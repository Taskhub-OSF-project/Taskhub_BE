package com.taskhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.DisputeRequest;
import com.taskhub.dto.request.DisputeResolveRequest;
import com.taskhub.dto.request.DisputeResolveRequest.DisputeAction;
import com.taskhub.dto.response.DisputeAIReport;
import com.taskhub.dto.response.DisputeResolveResponse;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Submission;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.SubmissionRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service xử lý nghiệp vụ Dispute (Phase 4).
 * Tách riêng khỏi TaskService và SubmissionService để giữ Single Responsibility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final TaskRepository taskRepo;
    private final SubmissionRepository submissionRepo;
    private final AiValidationService aiValidation;
    private final TaskService taskService;
    private final EscrowService escrowService;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────────
    // 1. Hirer mở dispute
    // ──────────────────────────────────────────────

    /**
     * POST /api/tasks/{id}/dispute
     * Chỉ hirer owner được gọi, task phải SUBMITTED.
     * Sinh AI report, lưu vào task, chuyển task → DISPUTED.
     */
    @Transactional
    public DisputeAIReport openDispute(Long taskId, DisputeRequest req) {
        if (req == null) {
            throw TaskHubException.badRequest("Request body is required");
        }

        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER) {
            throw TaskHubException.forbidden("Only hirers can open a dispute");
        }

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("Not your task");
        }
        if (task.getStatus() != TaskStatus.SUBMITTED) {
            throw TaskHubException.badRequest("Dispute can only be opened on SUBMITTED tasks");
        }

        // Lấy notes từ submission mới nhất để làm evidence cho AI
        String submissionNotes = getLatestSubmissionNotes(taskId);
        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription)
                .toList();

        // Sinh AI report
        DisputeAIReport report = aiValidation.generateStructuredDisputeReport(
                taskId,
                submissionNotes,
                criteria,
                req.getReason(),
                req.getDescription()
        );

        // Lưu dispute info + AI report vào task
        task.setDisputeReason(req.getReason());
        task.setDisputeDescription(req.getDescription());
        task.setDisputeAiReportJson(toJson(report));

        // Chuyển task → DISPUTED
        taskService.transition(task, TaskStatus.DISPUTED);
        taskRepo.save(task);

        log.info("Dispute opened for task {} by hirer {}. Recommendation: {}",
                taskId, hirer.getId(), report.getRecommendation());

        return report;
    }

    // ──────────────────────────────────────────────
    // 2. Xem báo cáo dispute
    // ──────────────────────────────────────────────

    /**
     * GET /api/tasks/{id}/dispute/report
     * Hirer owner hoặc assigned student được xem.
     * Task phải ở DISPUTED.
     */
    @Transactional(readOnly = true)
    public DisputeAIReport getDisputeReport(Long taskId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        boolean isHirerOwner = task.getHirer() != null
                && task.getHirer().getId().equals(currentUser.getId());
        boolean isAssignedStudent = task.getAssignedTo() != null
                && task.getAssignedTo().getId().equals(currentUser.getId());

        if (!isHirerOwner && !isAssignedStudent) {
            throw TaskHubException.forbidden("Not allowed to view dispute report");
        }
        if (task.getStatus() != TaskStatus.DISPUTED) {
            throw TaskHubException.badRequest("Task is not in DISPUTED status");
        }
        if (task.getDisputeAiReportJson() == null) {
            throw TaskHubException.badRequest("Dispute report not available");
        }

        return fromJson(task.getDisputeAiReportJson());
    }

    // ──────────────────────────────────────────────
    // 3. Giải quyết dispute
    // ──────────────────────────────────────────────

    /**
     * POST /api/tasks/{id}/dispute/resolve
     * Chỉ hirer owner, task phải DISPUTED.
     * action:
     *   RELEASE_PAYMENT → approve → COMPLETED + release escrow
     *   REQUEST_REVISION → refund + reset → IN_PROGRESS (giữ assignedTo)
     *   ESCALATE → ghi log, giữ DISPUTED (no-op MVP)
     */
    @Transactional
    public DisputeResolveResponse resolveDispute(Long taskId, DisputeResolveRequest req) {
        if (req == null || req.getAction() == null) {
            throw TaskHubException.badRequest("action is required");
        }

        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER) {
            throw TaskHubException.forbidden("Only hirers can resolve a dispute");
        }

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("Not your task");
        }
        if (task.getStatus() != TaskStatus.DISPUTED) {
            throw TaskHubException.badRequest("Task is not in DISPUTED status");
        }

        DisputeAction action = req.getAction();
        TaskStatus newStatus;
        String message;

        switch (action) {
            case RELEASE_PAYMENT -> {
                // Approve: task → COMPLETED, release escrow sang student
                taskService.transition(task, TaskStatus.COMPLETED);
                taskRepo.save(task);
                escrowService.releaseEscrow(taskId);
                newStatus = TaskStatus.COMPLETED;
                message = "Dispute resolved: payment released to student";
                log.info("Dispute resolved RELEASE_PAYMENT for task {} by hirer {}", taskId, hirer.getId());
            }
            case REQUEST_REVISION -> {
                // Refund hirer + task → IN_PROGRESS, giữ assignedTo để student làm lại
                escrowService.refundDisputeToRevision(taskId);
                // refundDisputeToRevision đã chuyển task → IN_PROGRESS, không cần transition lại
                newStatus = TaskStatus.IN_PROGRESS;
                message = "Dispute resolved: refund issued, task returned to IN_PROGRESS for revision";
                log.info("Dispute resolved REQUEST_REVISION for task {} by hirer {}", taskId, hirer.getId());
            }
            case ESCALATE -> {
                // MVP: ghi log thôi, task vẫn DISPUTED
                newStatus = TaskStatus.DISPUTED;
                message = "Dispute escalated. Task remains DISPUTED pending manual review.";
                log.warn("Dispute ESCALATED for task {} by hirer {}. Manual admin review required.", taskId, hirer.getId());
            }
            default -> throw TaskHubException.badRequest("Unknown action: " + action);
        }

        return DisputeResolveResponse.builder()
                .taskId(taskId)
                .newStatus(newStatus)
                .action(action.name())
                .message(message)
                .resolvedAt(LocalDateTime.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private String getLatestSubmissionNotes(Long taskId) {
        return submissionRepo.findTopByTaskIdOrderBySubmittedAtDesc(taskId)
                .map(Submission::getNotes)
                .orElse(null);
    }

    private String toJson(DisputeAIReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot serialize dispute AI report");
        }
    }

    private DisputeAIReport fromJson(String json) {
        try {
            return objectMapper.readValue(json, DisputeAIReport.class);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot parse dispute AI report");
        }
    }
}

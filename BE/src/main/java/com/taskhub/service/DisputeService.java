package com.taskhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.DisputeRequest;
import com.taskhub.dto.request.DisputeResolveRequest;
import com.taskhub.dto.request.DisputeResolveRequest.DisputeAction;
import com.taskhub.dto.response.DisputeAIReport;
import com.taskhub.dto.response.DisputeEventResponse;
import com.taskhub.dto.response.DisputeResolveResponse;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.DisputeEvent;
import com.taskhub.entity.Submission;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.NotificationType;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.DisputeEventRepository;
import com.taskhub.repository.SubmissionRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {
    private final TaskRepository taskRepo;
    private final SubmissionRepository submissionRepo;
    private final AiValidationService aiValidation;
    private final TaskService taskService;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final DisputeEventRepository disputeEventRepo;
    private final ObjectMapper objectMapper;

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

        String submissionNotes = getLatestSubmissionNotes(taskId);
        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription)
                .toList();

        DisputeAIReport report = aiValidation.generateStructuredDisputeReport(
                taskId, submissionNotes, criteria, req.getReason(), req.getDescription());

        task.setDisputeReason(req.getReason());
        task.setDisputeDescription(req.getDescription());
        task.setDisputeAiReportJson(toJson(report));
        taskService.transition(task, TaskStatus.DISPUTED);
        taskRepo.save(task);

        recordEvent(task, "DISPUTE_OPENED", hirer.getId(), hirer.getRole().name(),
                "Reason: " + req.getReason() + ". Description: " + req.getDescription(),
                report.getRecommendation(), null);

        if (task.getAssignedTo() != null) {
            notificationService.notify(
                    task.getAssignedTo().getId(),
                    NotificationType.TASK_DISPUTE_OPENED,
                    "Cong viec co dispute",
                    "Hirer da mo dispute cho: " + task.getTitle() + ". Ly do: " + req.getReason(),
                    "/tasks/" + taskId,
                    taskId);
        }

        log.info("Dispute opened for task {} by hirer {}. Recommendation: {}",
                taskId, hirer.getId(), report.getRecommendation());
        return report;
    }

    @Transactional(readOnly = true)
    public DisputeAIReport getDisputeReport(Long taskId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        boolean isHirerOwner = task.getHirer() != null && task.getHirer().getId().equals(currentUser.getId());
        boolean isAssignedStudent = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(currentUser.getId());

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

    @Transactional(readOnly = true)
    public List<DisputeEventResponse> getDisputeHistory(Long taskId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        boolean isHirerOwner = task.getHirer() != null && task.getHirer().getId().equals(currentUser.getId());
        boolean isAssignedStudent = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(currentUser.getId());

        if (!isHirerOwner && !isAssignedStudent) {
            throw TaskHubException.forbidden("Not allowed to view dispute history");
        }

        return disputeEventRepo.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toEventResponse).toList();
    }

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
                taskService.transition(task, TaskStatus.COMPLETED);
                taskRepo.save(task);
                escrowService.releaseEscrow(taskId);
                newStatus = TaskStatus.COMPLETED;
                message = "Dispute resolved: payment released to student";
                recordEvent(task, "RESOLVED_RELEASE", hirer.getId(), hirer.getRole().name(),
                        message, null, action.name());
                notifyDisputeResolved(task, message);
            }
            case REQUEST_REVISION -> {
                escrowService.resolveDisputeToRevision(taskId);
                newStatus = TaskStatus.IN_PROGRESS;
                message = "Dispute resolved: task returned for revision";
                recordEvent(task, "RESOLVED_REVISION", hirer.getId(), hirer.getRole().name(),
                        message, null, action.name());
                notifyDisputeResolved(task, message);
            }
            case ESCALATE -> {
                newStatus = TaskStatus.DISPUTED;
                message = "Dispute escalated to admin for manual review";
                recordEvent(task, "ESCALATED_TO_ADMIN", hirer.getId(), hirer.getRole().name(),
                        "Dispute escalated manually", null, action.name());
                notifyAdminsOfEscalation(task, hirer);
                log.warn("Dispute ESCALATED for task {} by hirer {}. Admin review required.", taskId, hirer.getId());
            }
            default -> throw TaskHubException.badRequest("Unknown action: " + action);
        }

        return DisputeResolveResponse.builder()
                .taskId(taskId).newStatus(newStatus).action(action.name())
                .message(message).resolvedAt(LocalDateTime.now()).build();
    }

    @Transactional
    public DisputeResolveResponse adminResolveDispute(Long taskId, DisputeResolveRequest req) {
        User admin = AuthUtil.getCurrentUser();

        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.DISPUTED) {
            throw TaskHubException.badRequest("Task is not in DISPUTED status");
        }

        if (req == null || req.getAction() == null) {
            throw TaskHubException.badRequest("action is required");
        }

        DisputeAction action = req.getAction();
        TaskStatus newStatus;
        String message;

        switch (action) {
            case RELEASE_PAYMENT -> {
                taskService.transition(task, TaskStatus.COMPLETED);
                taskRepo.save(task);
                escrowService.releaseEscrow(taskId);
                newStatus = TaskStatus.COMPLETED;
                message = "Admin resolved: payment released to student";
                recordEvent(task, "ADMIN_RESOLVED_RELEASE", admin.getId(), "ADMIN",
                        "Admin override: " + message, null, action.name());
            }
            case REQUEST_REVISION -> {
                escrowService.resolveDisputeToRevision(taskId);
                newStatus = TaskStatus.IN_PROGRESS;
                message = "Admin resolved: task returned for revision";
                recordEvent(task, "ADMIN_RESOLVED_REVISION", admin.getId(), "ADMIN",
                        "Admin override: " + message, null, action.name());
            }
            case ESCALATE -> {
                newStatus = TaskStatus.DISPUTED;
                message = "Admin escalation requires internal review";
                recordEvent(task, "ADMIN_REVIEWED", admin.getId(), "ADMIN",
                        "Admin reviewed, no action taken yet", null, action.name());
            }
            default -> throw TaskHubException.badRequest("Unknown action: " + action);
        }

        notifyParticipantsOfAdminResolution(task, message);

        return DisputeResolveResponse.builder()
                .taskId(taskId).newStatus(newStatus).action(action.name())
                .message(message).resolvedAt(LocalDateTime.now()).build();
    }

    private void recordEvent(Task task, String eventType, Long performedBy, String role,
                             String details, String aiRecommendation, String actionTaken) {
        DisputeEvent event = DisputeEvent.builder()
                .task(task).eventType(eventType).performedBy(performedBy)
                .performedByRole(role).details(details)
                .aiRecommendation(aiRecommendation).actionTaken(actionTaken).build();
        disputeEventRepo.save(event);
    }

    private void notifyDisputeResolved(Task task, String message) {
        if (task.getAssignedTo() != null) {
            notificationService.notify(
                    task.getAssignedTo().getId(),
                    NotificationType.TASK_DISPUTE_RESOLVED,
                    "Dispute da duoc giai quyet",
                    task.getTitle() + ": " + message,
                    "/tasks/" + task.getId(),
                    task.getId());
        }
    }

    private void notifyAdminsOfEscalation(Task task, User hirer) {
        log.warn("Dispute ESCALATED to admin for task {} by hirer {}", task.getId(), hirer.getId());
    }

    private void notifyParticipantsOfAdminResolution(Task task, String message) {
        if (task.getHirer() != null) {
            notificationService.notify(task.getHirer().getId(),
                    NotificationType.TASK_DISPUTE_RESOLVED, "Admin da giai quyet dispute",
                    task.getTitle() + ": " + message, "/tasks/" + task.getId(), task.getId());
        }
        if (task.getAssignedTo() != null) {
            notificationService.notify(task.getAssignedTo().getId(),
                    NotificationType.TASK_DISPUTE_RESOLVED, "Admin da giai quyet dispute",
                    task.getTitle() + ": " + message, "/tasks/" + task.getId(), task.getId());
        }
    }

    private String getLatestSubmissionNotes(Long taskId) {
        return submissionRepo.findTopByTaskIdOrderBySubmittedAtDesc(taskId)
                .map(Submission::getNotes).orElse(null);
    }

    private String toJson(DisputeAIReport report) {
        try { return objectMapper.writeValueAsString(report); }
        catch (JsonProcessingException ex) { throw TaskHubException.internalError("Cannot serialize dispute AI report"); }
    }

    private DisputeAIReport fromJson(String json) {
        try { return objectMapper.readValue(json, DisputeAIReport.class); }
        catch (JsonProcessingException ex) { throw TaskHubException.internalError("Cannot parse dispute AI report"); }
    }

    private DisputeEventResponse toEventResponse(DisputeEvent e) {
        return DisputeEventResponse.builder()
                .id(e.getId()).taskId(e.getTask().getId()).eventType(e.getEventType())
                .performedBy(e.getPerformedBy()).performedByRole(e.getPerformedByRole())
                .details(e.getDetails()).aiRecommendation(e.getAiRecommendation())
                .actionTaken(e.getActionTaken()).createdAt(e.getCreatedAt()).build();
    }
}

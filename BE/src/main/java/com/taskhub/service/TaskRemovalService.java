package com.taskhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.TaskRemovalRequestDto;
import com.taskhub.dto.request.TaskRemovalResolveDto;
import com.taskhub.dto.response.RemovalAIReport;
import com.taskhub.dto.response.TaskRemovalResponse;
import com.taskhub.entity.Escrow;
import com.taskhub.entity.Submission;
import com.taskhub.entity.Task;
import com.taskhub.entity.TaskRemovalRequest;
import com.taskhub.entity.User;
import com.taskhub.enums.NotificationType;
import com.taskhub.enums.RemovalReason;
import com.taskhub.enums.RemovalStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.EscrowRepository;
import com.taskhub.repository.SubmissionRepository;
import com.taskhub.repository.TaskRemovalRequestRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskRemovalService {
    private final TaskRemovalRequestRepository removalRepo;
    private final TaskRepository taskRepo;
    private final SubmissionRepository submissionRepo;
    private final EscrowRepository escrowRepo;
    private final TaskService taskService;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TaskRemovalResponse requestRemoval(Long taskId, TaskRemovalRequestDto req) {
        User hirer = AuthUtil.getCurrentUser();

        if (hirer.getRole() != Role.HIRER && hirer.getRole() != Role.ADMIN) {
            throw TaskHubException.forbidden("Chỉ hirer mới có thể yêu cầu gỡ job");
        }

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId()) && hirer.getRole() != Role.ADMIN) {
            throw TaskHubException.forbidden("Bạn không phải là chủ sở hữu của job này");
        }

        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw TaskHubException.badRequest("Không thể gỡ job đã hoàn thành");
        }

        if (task.getStatus() == TaskStatus.DISPUTED) {
            throw TaskHubException.badRequest("Job đang trong tranh chấp, không thể gỡ");
        }

        if (removalRepo.existsByTaskIdAndStatus(taskId, RemovalStatus.PENDING)) {
            throw TaskHubException.badRequest("Đã có yêu cầu gỡ job đang chờ duyệt cho job này");
        }

        // AI validation
        RemovalAIReport aiReport = generateAIRemovalReport(task, req.getReason(), req.getReasonDescription());
        String aiReportJson = toJson(aiReport);

        TaskRemovalRequest removal = TaskRemovalRequest.builder()
                .task(task)
                .requestedBy(hirer)
                .reason(req.getReason())
                .reasonDescription(req.getReasonDescription())
                .taskStatusAtRequest(task.getStatus())
                .status(RemovalStatus.PENDING)
                .aiValidationResult(aiReportJson)
                .aiRecommendation(aiReport.getAiRecommendation())
                .build();

        removal = removalRepo.save(removal);

        // Transition task to REMOVAL_REQUESTED
        taskService.transition(task, TaskStatus.REMOVAL_REQUESTED);
        taskRepo.save(task);

        // Notify admins
        notifyAdminsOfRemovalRequest(removal);

        log.info("Removal request created for task {} by hirer {}. AI Recommendation: {}",
                taskId, hirer.getId(), aiReport.getAiRecommendation());

        return toResponse(removal);
    }

    @Transactional(readOnly = true)
    public RemovalAIReport getAIRemovalReport(Long taskId) {
        Task task = taskService.findTask(taskId);
        User currentUser = AuthUtil.getCurrentUser();

        if (!task.getHirer().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw TaskHubException.forbidden("Bạn không có quyền xem báo cáo này");
        }

        return removalRepo.findByTaskIdAndStatus(taskId, RemovalStatus.PENDING)
                .map(req -> fromJson(req.getAiValidationResult(), RemovalAIReport.class))
                .orElseThrow(() -> TaskHubException.badRequest("Không tìm thấy yêu cầu gỡ job đang chờ duyệt"));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskRemovalResponse> getMyRemovalRequests(PageRequestDto pageReq) {
        User currentUser = AuthUtil.getCurrentUser();
        PageRequest page = PageRequest.of(pageReq.getPage(), pageReq.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskRemovalRequest> pageResult = removalRepo.findByRequestedByIdOrderByCreatedAtDesc(currentUser.getId(), page);
        return toPageResponse(pageResult);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskRemovalResponse> getPendingRemovalRequests(PageRequestDto pageReq) {
        PageRequest page = PageRequest.of(pageReq.getPage(), pageReq.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskRemovalRequest> pageResult = removalRepo.findByStatusOrderByCreatedAtDesc(RemovalStatus.PENDING, page);
        return toPageResponse(pageResult);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskRemovalResponse> getAllRemovalRequests(PageRequestDto pageReq) {
        PageRequest page = PageRequest.of(pageReq.getPage(), pageReq.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskRemovalRequest> pageResult = removalRepo.findAllByOrderByCreatedAtDesc(page);
        return toPageResponse(pageResult);
    }

    @Transactional
    public TaskRemovalResponse resolveRemovalRequest(Long removalId, TaskRemovalResolveDto req) {
        User admin = AuthUtil.getCurrentUser();
        if (admin.getRole() != Role.ADMIN) {
            throw TaskHubException.forbidden("Chỉ admin mới có thể duyệt yêu cầu gỡ job");
        }

        TaskRemovalRequest removal = removalRepo.findByIdWithDetails(removalId)
                .orElseThrow(() -> TaskHubException.notFound("Không tìm thấy yêu cầu gỡ job"));

        if (removal.getStatus() != RemovalStatus.PENDING) {
            throw TaskHubException.badRequest("Yêu cầu này đã được xử lý");
        }

        Task task = removal.getTask();
        removal.setAdminId(admin.getId());
        removal.setAdminNotes(req.getAdminNotes());
        removal.setResolvedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(req.getApproved())) {
            removal.setStatus(RemovalStatus.APPROVED);
            handleApproval(task, removal);
            log.info("Removal request {} APPROVED by admin {}", removalId, admin.getId());
        } else {
            removal.setStatus(RemovalStatus.REJECTED);
            handleRejection(task, removal);
            log.info("Removal request {} REJECTED by admin {}", removalId, admin.getId());
        }

        removal = removalRepo.save(removal);
        return toResponse(removal);
    }

    private void handleApproval(Task task, TaskRemovalRequest removal) {
        TaskStatus originalStatus = removal.getTaskStatusAtRequest();

        // Check if task has escrow and refund
        escrowRepo.findByTaskId(task.getId()).ifPresent(escrow -> {
            escrowService.refundEscrow(task.getId());
        });

        // Transition task back to DRAFT (allows hirer to edit/delete) or delete
        taskService.transition(task, TaskStatus.DRAFT);
        taskRepo.save(task);

        // Notify hirer
        notificationService.notify(
                removal.getRequestedBy().getId(),
                NotificationType.TASK_REMOVAL_APPROVED,
                "Yêu cầu gỡ job được duyệt",
                "Yêu cầu gỡ job '" + task.getTitle() + "' đã được admin duyệt. Job đã được đưa về trạng thái DRAFT.",
                "/hirer/tasks/" + task.getId(),
                task.getId());
    }

    private void handleRejection(Task task, TaskRemovalRequest removal) {
        // Restore task to original status
        TaskStatus originalStatus = removal.getTaskStatusAtRequest();
        if (originalStatus == TaskStatus.REMOVAL_REQUESTED) {
            originalStatus = TaskStatus.ACTIVE;
        }

        if (task.getStatus() == TaskStatus.REMOVAL_REQUESTED) {
            taskService.transition(task, originalStatus);
            taskRepo.save(task);
        }

        // Notify hirer
        notificationService.notify(
                removal.getRequestedBy().getId(),
                NotificationType.TASK_REMOVAL_REJECTED,
                "Yêu cầu gỡ job bị từ chối",
                "Yêu cầu gỡ job '" + task.getTitle() + "' đã bị admin từ chối. Job tiếp tục hoạt động.",
                "/hirer/tasks/" + task.getId(),
                task.getId());
    }

    private RemovalAIReport generateAIRemovalReport(Task task, RemovalReason reason, String description) {
        List<String> warnings = new ArrayList<>();

        boolean hasAssignedFreelancer = task.getAssignedTo() != null;
        boolean hasEscrow = escrowRepo.findByTaskId(task.getId()).isPresent();
        Escrow escrow = escrowRepo.findByTaskId(task.getId()).orElse(null);
        boolean hasSubmissions = submissionRepo.countByTaskId(task.getId()) > 0;
        int submissionCount = (int) submissionRepo.countByTaskId(task.getId());
        int revisionCount = task.getRevisionCount() != null ? task.getRevisionCount() : 0;

        // Determine AI recommendation
        String recommendation;
        String analysis;
        boolean canAutoApprove = false;

        switch (reason) {
            case DUPLICATE, MISPOSTED -> {
                if (!hasAssignedFreelancer && !hasSubmissions) {
                    recommendation = "AUTO_APPROVE";
                    analysis = "Lý do '" + reason.getLabel() + "' là hợp lý. Job chưa có freelancer hay submission, có thể duyệt tự động.";
                    canAutoApprove = true;
                } else if (hasAssignedFreelancer || hasSubmissions) {
                    recommendation = "NEED_REVIEW";
                    analysis = "Lý do '" + reason.getLabel() + "' có thể hợp lý nhưng job đã có freelancer hoặc submissions. Cần xem xét kỹ.";
                    warnings.add("Job đã có freelancer được giao: " + (hasAssignedFreelancer ? "Có" : "Không"));
                    warnings.add("Job đã có submissions: " + submissionCount);
                } else {
                    recommendation = "NEED_REVIEW";
                    analysis = "Cần admin xem xét yêu cầu này.";
                }
            }
            case NO_LONGER_NEEDED, PROJECT_CANCELLED, BUDGET_ISSUES -> {
                if (hasAssignedFreelancer && !hasSubmissions) {
                    recommendation = "REFUND_FREELANCER";
                    analysis = "Job đã giao cho freelancer nhưng chưa có submission. Nên hoàn tiền cho freelancer và gỡ job.";
                    warnings.add("Freelancer đang chờ làm việc");
                    if (escrow != null) {
                        warnings.add("Số tiền escrow: " + escrow.getAmount());
                    }
                } else if (hasSubmissions) {
                    recommendation = "NEED_REVIEW";
                    analysis = "Job đã có submissions. Cần admin xem xét mức độ hoàn thành để quyết định hoàn tiền.";
                    warnings.add("Có " + submissionCount + " submissions");
                    if (revisionCount > 0) {
                        warnings.add("Đã có " + revisionCount + " lần yêu cầu sửa đổi");
                    }
                } else {
                    recommendation = "AUTO_APPROVE";
                    analysis = "Job chưa bắt đầu, có thể gỡ và hoàn tiền escrow.";
                    canAutoApprove = true;
                }
            }
            case FOUND_BETTER_FREELANCER -> {
                if (hasAssignedFreelancer) {
                    recommendation = "NEED_REVIEW";
                    analysis = "Đã có freelancer làm việc. Cần xem xét công việc đã thực hiện và hoàn tiền phù hợp.";
                    warnings.add("Freelancer đã được giao công việc");
                    warnings.add("Cần đảm bảo công bằng cho freelancer");
                } else {
                    recommendation = "AUTO_APPROVE";
                    analysis = "Chưa có freelancer, có thể duyệt gỡ job.";
                    canAutoApprove = true;
                }
            }
            case OTHER -> {
                recommendation = "NEED_REVIEW";
                analysis = "Lý do khác - cần admin xem xét chi tiết: " + (description != null ? description : "Không có mô tả");
                warnings.add("Lý do không xác định rõ ràng");
            }
            default -> {
                recommendation = "NEED_REVIEW";
                analysis = "Cần admin xem xét yêu cầu này.";
            }
        }

        return RemovalAIReport.builder()
                .taskId(task.getId())
                .taskTitle(task.getTitle())
                .removalReason(reason.getLabel())
                .reasonDescription(description)
                .taskStatus(task.getStatus().name())
                .hasAssignedFreelancer(hasAssignedFreelancer)
                .hasEscrow(hasEscrow)
                .hasSubmissions(hasSubmissions)
                .revisionCount(revisionCount)
                .submissionCount(submissionCount)
                .escrowAmount(escrow != null ? escrow.getAmount().doubleValue() : 0.0)
                .aiRecommendation(recommendation)
                .aiAnalysis(analysis)
                .warnings(warnings)
                .canAutoApprove(canAutoApprove)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private void notifyAdminsOfRemovalRequest(TaskRemovalRequest removal) {
        log.info("New removal request for task {} - Reason: {} - AI Recommendation: {}",
                removal.getTask().getId(), removal.getReason().getLabel(), removal.getAiRecommendation());
    }

    private TaskRemovalResponse toResponse(TaskRemovalRequest req) {
        Task task = req.getTask();
        return TaskRemovalResponse.builder()
                .id(req.getId())
                .taskId(task.getId())
                .taskTitle(task.getTitle())
                .taskStatus(task.getStatus())
                .requestedById(req.getRequestedBy().getId())
                .requestedByName(req.getRequestedBy().getFullName() != null ? req.getRequestedBy().getFullName() : req.getRequestedBy().getEmail())
                .reason(req.getReason())
                .reasonLabel(req.getReason().getLabel())
                .reasonDescription(req.getReasonDescription())
                .taskStatusAtRequest(req.getTaskStatusAtRequest())
                .status(req.getStatus())
                .statusLabel(req.getStatus().getLabel())
                .aiValidationResult(req.getAiValidationResult())
                .aiRecommendation(req.getAiRecommendation())
                .adminId(req.getAdminId())
                .adminNotes(req.getAdminNotes())
                .resolvedAt(req.getResolvedAt())
                .createdAt(req.getCreatedAt())
                .build();
    }

    private PageResponse<TaskRemovalResponse> toPageResponse(Page<TaskRemovalRequest> page) {
        List<TaskRemovalResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return PageResponse.<TaskRemovalResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot serialize AI report");
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot parse AI report");
        }
    }
}

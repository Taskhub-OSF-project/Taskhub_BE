package com.taskhub.service;

import com.taskhub.dto.request.CreateMilestoneRequest;
import com.taskhub.dto.response.MilestoneResponse;
import com.taskhub.entity.Milestone;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.EscrowStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.enums.WalletTransactionType;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.MilestoneRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.AuthUtil;
import com.taskhub.util.EscrowCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneService {
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public List<MilestoneResponse> getMilestonesByTask(Long taskId) {
        Task task = taskService.findTask(taskId);
        return milestoneRepository.findByTaskIdOrderByDisplayOrder(taskId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public MilestoneResponse createMilestone(Long taskId, CreateMilestoneRequest req) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        if (!task.getHirer().getId().equals(currentUser.getId())) {
            throw TaskHubException.forbidden("Only the task owner can add milestones");
        }
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw TaskHubException.badRequest("Can only add milestones to DRAFT tasks");
        }

        int nextOrder = milestoneRepository.findByTaskIdOrderByDisplayOrder(taskId).size();

        Milestone milestone = Milestone.builder()
                .task(task)
                .title(req.getTitle().trim())
                .description(req.getDescription())
                .amount(req.getAmount())
                .dueDate(req.getDueDate())
                .displayOrder(nextOrder)
                .status(Milestone.MilestoneStatus.PENDING)
                .escrowStatus(EscrowStatus.PENDING)
                .build();
        milestone = milestoneRepository.save(milestone);

        log.info("Milestone created: id={}, taskId={}, title={}", milestone.getId(), taskId, milestone.getTitle());
        return toResponse(milestone);
    }

    @Transactional
    public MilestoneResponse updateMilestone(Long taskId, Long milestoneId, CreateMilestoneRequest req) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        if (!task.getHirer().getId().equals(currentUser.getId())) {
            throw TaskHubException.forbidden("Only the task owner can update milestones");
        }
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw TaskHubException.badRequest("Can only update milestones of DRAFT tasks");
        }

        Milestone milestone = milestoneRepository.findByIdAndTaskId(milestoneId, taskId)
                .orElseThrow(() -> TaskHubException.notFound("Milestone not found"));

        if (milestone.getEscrowStatus() != EscrowStatus.PENDING) {
            throw TaskHubException.badRequest("Cannot update milestone with funded escrow");
        }

        milestone.setTitle(req.getTitle().trim());
        milestone.setDescription(req.getDescription());
        milestone.setAmount(req.getAmount());
        milestone.setDueDate(req.getDueDate());
        milestone = milestoneRepository.save(milestone);

        return toResponse(milestone);
    }

    @Transactional
    public void deleteMilestone(Long taskId, Long milestoneId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        if (!task.getHirer().getId().equals(currentUser.getId())) {
            throw TaskHubException.forbidden("Only the task owner can delete milestones");
        }
        if (task.getStatus() != TaskStatus.DRAFT) {
            throw TaskHubException.badRequest("Can only delete milestones of DRAFT tasks");
        }

        Milestone milestone = milestoneRepository.findByIdAndTaskId(milestoneId, taskId)
                .orElseThrow(() -> TaskHubException.notFound("Milestone not found"));

        if (milestone.getEscrowStatus() == EscrowStatus.FUNDED) {
            throw TaskHubException.badRequest("Cannot delete a funded milestone. Refund escrow first.");
        }

        milestoneRepository.delete(milestone);
        log.info("Milestone deleted: id={}, taskId={}", milestoneId, taskId);
    }

    @Transactional
    public MilestoneResponse fundMilestone(Long taskId, Long milestoneId) {
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER) {
            throw TaskHubException.forbidden("Only hirers can fund milestone escrow");
        }

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("Not your task");
        }

        Milestone milestone = milestoneRepository.findByIdAndTaskId(milestoneId, taskId)
                .orElseThrow(() -> TaskHubException.notFound("Milestone not found"));

        if (milestone.getEscrowStatus() == EscrowStatus.FUNDED) {
            throw TaskHubException.badRequest("Milestone already funded");
        }

        BigDecimal platformFee = EscrowCalculator.platformFee(milestone.getAmount());
        BigDecimal totalDeduction = milestone.getAmount().add(platformFee);

        if (hirer.getWalletBalance().compareTo(totalDeduction) < 0) {
            throw TaskHubException.insufficientWallet("Insufficient balance for milestone escrow", null);
        }

        hirer.setWalletBalance(hirer.getWalletBalance().subtract(totalDeduction));
        userRepository.save(hirer);
        walletService.recordTransaction(hirer, WalletTransactionType.escrow_deduction, totalDeduction.negate(), task);

        milestone.setEscrowStatus(EscrowStatus.FUNDED);
        milestone.setFundedAt(java.time.LocalDateTime.now());
        milestone = milestoneRepository.save(milestone);

        updateTaskStatusFromMilestones(task);

        log.info("Milestone funded: id={}, taskId={}, amount={}", milestoneId, taskId, milestone.getAmount());
        return toResponse(milestone);
    }

    @Transactional
    public MilestoneResponse approveMilestone(Long taskId, Long milestoneId) {
        User hirer = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        if (!task.getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("Only task owner can approve milestones");
        }

        Milestone milestone = milestoneRepository.findByIdAndTaskId(milestoneId, taskId)
                .orElseThrow(() -> TaskHubException.notFound("Milestone not found"));

        if (milestone.getEscrowStatus() != EscrowStatus.FUNDED) {
            throw TaskHubException.badRequest("Milestone escrow must be funded first");
        }

        User student = task.getAssignedTo();
        if (student == null) {
            throw TaskHubException.badRequest("Task has no assigned freelancer");
        }

        milestone.setStatus(Milestone.MilestoneStatus.APPROVED);
        milestone.setReleasedAt(java.time.LocalDateTime.now());
        milestone.setEscrowStatus(EscrowStatus.RELEASED);
        milestone = milestoneRepository.save(milestone);

        BigDecimal netAmount = milestone.getAmount();
        student.setWalletBalance(student.getWalletBalance().add(netAmount));
        userRepository.save(student);
        walletService.recordTransaction(student, WalletTransactionType.escrow_release, netAmount, task);

        updateTaskStatusFromMilestones(task);

        log.info("Milestone approved: id={}, taskId={}, released={}", milestoneId, taskId, netAmount);
        return toResponse(milestone);
    }

    @Transactional
    public MilestoneResponse rejectMilestone(Long taskId, Long milestoneId) {
        User hirer = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        if (!task.getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("Only task owner can reject milestones");
        }

        Milestone milestone = milestoneRepository.findByIdAndTaskId(milestoneId, taskId)
                .orElseThrow(() -> TaskHubException.notFound("Milestone not found"));

        if (milestone.getEscrowStatus() != EscrowStatus.FUNDED) {
            throw TaskHubException.badRequest("Milestone escrow must be funded first");
        }

        milestone.setStatus(Milestone.MilestoneStatus.REJECTED);
        milestone.setEscrowStatus(EscrowStatus.REFUNDED);
        milestone.setReleasedAt(java.time.LocalDateTime.now());
        milestone = milestoneRepository.save(milestone);

        BigDecimal refundAmount = milestone.getAmount();
        hirer.setWalletBalance(hirer.getWalletBalance().add(refundAmount));
        userRepository.save(hirer);
        walletService.recordTransaction(hirer, WalletTransactionType.refund, refundAmount, task);

        log.info("Milestone rejected: id={}, taskId={}, refunded={}", milestoneId, taskId, refundAmount);
        return toResponse(milestone);
    }

    private void updateTaskStatusFromMilestones(Task task) {
        List<Milestone> milestones = milestoneRepository.findByTaskIdOrderByDisplayOrder(task.getId());
        if (milestones.isEmpty()) return;

        long fundedCount = milestoneRepository.countByTaskIdAndEscrowStatus(task.getId(), EscrowStatus.FUNDED);
        long releasedCount = milestoneRepository.countByTaskIdAndEscrowStatus(task.getId(), EscrowStatus.RELEASED);
        long totalCount = milestones.size();

        if (releasedCount == totalCount) {
            taskService.transition(task, TaskStatus.COMPLETED);
        } else if (fundedCount > 0) {
            taskService.transition(task, TaskStatus.ACTIVE);
        }
        taskRepository.save(task);
    }

    private MilestoneResponse toResponse(Milestone m) {
        return MilestoneResponse.builder()
                .id(m.getId())
                .taskId(m.getTask().getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .amount(m.getAmount())
                .dueDate(m.getDueDate())
                .displayOrder(m.getDisplayOrder())
                .status(m.getStatus().name())
                .escrowStatus(m.getEscrowStatus().name())
                .fundedAt(m.getFundedAt())
                .releasedAt(m.getReleasedAt())
                .createdAt(m.getCreatedAt())
                .build();
    }
}

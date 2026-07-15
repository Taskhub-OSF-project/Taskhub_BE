package com.taskhub.service;

import com.taskhub.entity.Escrow;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.CriteriaStatus;
import com.taskhub.enums.EscrowStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.enums.WalletTransactionType;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.EscrowRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.repository.TaskApplicationRepository;
import com.taskhub.repository.MilestoneRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskhub.util.EscrowCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service xử lý nghiệp vụ escrow (fund/release/refund).
 * Thuộc module Escrow, được gọi từ EscrowController và SubmissionService.
 */
@Service
@RequiredArgsConstructor
public class EscrowService {

    private final EscrowRepository escrowRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final WalletService walletService;
    private final TaskApplicationRepository appRepository;
    private final MilestoneRepository milestoneRepository;

    /**
     * Kiểm tra escrow của task có đang ở trạng thái FUNDED hay không.
     */
    @Transactional(readOnly = true)
    public boolean isEscrowFunded(Long taskId) {
        boolean taskEscrowFunded = escrowRepository.findByTaskId(taskId)
                .map(escrow -> escrow.getStatus() == EscrowStatus.FUNDED)
                .orElse(false);
        if (taskEscrowFunded) {
            return true;
        }
        var milestones = milestoneRepository.findByTaskIdOrderByDisplayOrder(taskId);
        return !milestones.isEmpty()
                && milestones.stream().allMatch(m -> m.getEscrowStatus() == EscrowStatus.FUNDED
                        || m.getEscrowStatus() == EscrowStatus.RELEASED);
    }

    /**
     * Nạp escrow cho task (HIRER owner).
     * Rule: task phải cho phép transition -> ESCROW_FUNDED, ví đủ budget + fee.
     */
    @Transactional
    public void fundEscrow(Long taskId) {
        User hirer = AuthUtil.getCurrentUser();
        // Chỉ HIRER được fund escrow.
        if (hirer.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can fund escrow");

        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        taskService.validateTransition(task, TaskStatus.ESCROW_FUNDED);
        if (!milestoneRepository.findByTaskIdOrderByDisplayOrder(taskId).isEmpty()) {
            throw TaskHubException.badRequest("Task escrow and milestone escrow are mutually exclusive");
        }

        Escrow escrow = escrowRepository.findByTaskIdForUpdate(taskId)
                .orElseGet(() -> Escrow.builder().task(task).build());
        if (escrow.getStatus() == EscrowStatus.FUNDED)
            throw TaskHubException.badRequest("Escrow already funded");

        hirer = userRepository.findByIdForUpdate(hirer.getId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        BigDecimal platformFee = EscrowCalculator.platformFee(task.getBudget());
        BigDecimal totalDeduction = EscrowCalculator.totalEscrowDeduction(task.getBudget());
        if (hirer.getWalletBalance().compareTo(totalDeduction) < 0) {
            throw TaskHubException.insufficientWallet(
                    "So du vi khong du de nap escrow. Vui long nap them tien.",
                    walletService.assessCreateTaskReadiness(task.getBudget()));
        }

        // Trừ tiền ví và ghi ledger escrow_deduction.
        hirer.setWalletBalance(hirer.getWalletBalance().subtract(totalDeduction));
        userRepository.save(hirer);
        walletService.recordTransaction(hirer, WalletTransactionType.escrow_deduction, totalDeduction.negate(), task);

        escrow.setAmount(task.getBudget());
        escrow.setPlatformFee(platformFee);
        escrow.setStatus(EscrowStatus.FUNDED);
        escrowRepository.save(escrow);

        taskService.transition(task, TaskStatus.ESCROW_FUNDED);
        taskRepository.save(task);
    }

    /**
     * Releases escrow to the assigned student and completes submitted/disputed tasks.
     */
    @Transactional
    public void releaseEscrow(Long taskId) {
        User actor = AuthUtil.getCurrentUser();
        if (actor.getRole() != Role.HIRER && actor.getRole() != Role.ADMIN)
            throw TaskHubException.forbidden("Only the task owner or an admin can release escrow");

        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        if (actor.getRole() != Role.ADMIN && !task.getHirer().getId().equals(actor.getId()))
            throw TaskHubException.forbidden("Not your task");

        var milestones = milestoneRepository.findByTaskIdOrderByDisplayOrder(taskId);
        if (!milestones.isEmpty()) {
            releaseMilestoneEscrow(task, milestones);
            return;
        }

        Escrow escrow = escrowRepository.findByTaskIdForUpdate(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Escrow not found"));
        if (escrow.getStatus() == EscrowStatus.RELEASED)
            return;
        if (escrow.getStatus() != EscrowStatus.FUNDED)
            throw TaskHubException.badRequest("Escrow not in FUNDED state");

        if (task.getStatus() == TaskStatus.SUBMITTED || task.getStatus() == TaskStatus.DISPUTED) {
            task.getAcceptanceCriteria().forEach(c -> c.setStatus(CriteriaStatus.PASSED));
            taskService.transition(task, TaskStatus.COMPLETED);
            taskRepository.save(task);
        } else if (task.getStatus() != TaskStatus.COMPLETED) {
            throw TaskHubException.badRequest("Task must be SUBMITTED, DISPUTED or COMPLETED to release escrow");
        }

        User assignedStudent = task.getAssignedTo();
        if (assignedStudent == null)
            throw TaskHubException.badRequest("Task has no assigned student");
        User student = userRepository.findByIdForUpdate(assignedStudent.getId())
                .orElseThrow(() -> TaskHubException.notFound("Assigned student not found"));

        // Cộng tiền cho student và ghi ledger escrow_release.
        student.setWalletBalance(student.getWalletBalance().add(escrow.getAmount()));
        userRepository.save(student);
        walletService.recordTransaction(student, WalletTransactionType.escrow_release, escrow.getAmount(), task);

        escrow.setStatus(EscrowStatus.RELEASED);
        escrowRepository.save(escrow);
    }

    /**
     * Refund escrow về ví hirer khi dispute.
     * Rule: task cho phép transition về LOCKED.
     */
    @Transactional
    public void refundEscrow(Long taskId) {
        User actor = AuthUtil.getCurrentUser();
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        boolean isAdmin = actor.getRole() == Role.ADMIN;
        boolean isOwner = actor.getRole() == Role.HIRER
                && task.getHirer().getId().equals(actor.getId());
        if (!isAdmin && !isOwner)
            throw TaskHubException.forbidden("Not your task");
        taskService.validateTransition(task, TaskStatus.LOCKED);

        User hirer = userRepository.findByIdForUpdate(task.getHirer().getId())
                .orElseThrow(() -> TaskHubException.notFound("Task owner not found"));
        BigDecimal refundAmount;
        var milestones = milestoneRepository.findByTaskIdOrderByDisplayOrder(taskId);
        var fundedMilestones = milestones.stream()
                .filter(m -> m.getEscrowStatus() == EscrowStatus.FUNDED)
                .toList();
        if (!fundedMilestones.isEmpty()) {
            refundAmount = fundedMilestones.stream()
                    .map(m -> EscrowCalculator.totalEscrowDeduction(m.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            fundedMilestones.forEach(m -> m.setEscrowStatus(EscrowStatus.REFUNDED));
            milestoneRepository.saveAll(fundedMilestones);
        } else {
            Escrow escrow = escrowRepository.findByTaskIdForUpdate(taskId)
                    .orElseThrow(() -> TaskHubException.notFound("Escrow not found"));
            if (escrow.getStatus() != EscrowStatus.FUNDED)
                throw TaskHubException.badRequest("Escrow not in FUNDED state");
            refundAmount = escrow.getAmount().add(escrow.getPlatformFee());
            escrow.setStatus(EscrowStatus.REFUNDED);
            escrowRepository.save(escrow);
        }

        // Trả lại tiền cho hirer và ghi ledger refund.
        hirer.setWalletBalance(hirer.getWalletBalance().add(refundAmount));
        userRepository.save(hirer);
        walletService.recordTransaction(hirer, WalletTransactionType.refund, refundAmount, task);

        // Reset assignee và criteria khi hoàn tiền.
        task.setAssignedTo(null);
        task.getAcceptanceCriteria().forEach(c -> c.setStatus(CriteriaStatus.PENDING));
        task.setApplicantCount(0);
        taskService.transition(task, TaskStatus.LOCKED);
        taskRepository.save(task);

        // Xóa toàn bộ application cũ để cho phép ứng tuyển lại khi đăng lại task
        var applications = appRepository.findByTaskId(taskId);
        appRepository.deleteAll(applications);
    }

    /**
     * Resolve dispute với action REQUEST_REVISION.
     * Khác với refundEscrow():
     * - KHÔNG hoàn tiền (escrow vẫn ở trạng thái FUNDED để đảm bảo thanh toán)
     * - KHÔNG clear assignedTo (student vẫn giữ task)
     * - Task về IN_PROGRESS (thay vì LOCKED)
     * - Không cần validateTransition từ LOCKED, dùng transition DISPUTED → IN_PROGRESS
     */
    @Transactional
    public void resolveDisputeToRevision(Long taskId) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        if (task.getStatus() != TaskStatus.DISPUTED)
            throw TaskHubException.badRequest("Task must be DISPUTED to request revision");

        if (!isEscrowFunded(taskId))
            throw TaskHubException.badRequest("Escrow not in FUNDED state");

        // Giữ assignedTo, reset AI result + precheck để student có thể precheck lại
        task.setSubmissionAIResultJson(null);
        task.setLatestPrecheckAt(null);
        task.setPrecheckStudentId(null);
        task.setPrecheckCanSubmit(null);
        task.setPrecheckSubmittedFilePathsJson(null);

        // Task → IN_PROGRESS (student vẫn được assign, escrow vẫn FUNDED)
        taskService.transition(task, TaskStatus.IN_PROGRESS);
        taskRepository.save(task);
    }

    private void releaseMilestoneEscrow(Task task, java.util.List<com.taskhub.entity.Milestone> milestones) {
        if (task.getStatus() != TaskStatus.SUBMITTED
                && task.getStatus() != TaskStatus.DISPUTED
                && task.getStatus() != TaskStatus.COMPLETED) {
            throw TaskHubException.badRequest("Task must be SUBMITTED, DISPUTED or COMPLETED to release escrow");
        }

        var funded = milestones.stream()
                .filter(m -> m.getEscrowStatus() == EscrowStatus.FUNDED)
                .toList();
        if (funded.isEmpty()) {
            if (milestones.stream().allMatch(m -> m.getEscrowStatus() == EscrowStatus.RELEASED)) {
                return;
            }
            throw TaskHubException.badRequest("All milestone escrows must be funded before release");
        }
        if (funded.size() != milestones.stream()
                .filter(m -> m.getEscrowStatus() != EscrowStatus.RELEASED).count()) {
            throw TaskHubException.badRequest("Milestone escrow states are inconsistent");
        }

        User assignedStudent = task.getAssignedTo();
        if (assignedStudent == null) {
            throw TaskHubException.badRequest("Task has no assigned student");
        }
        User student = userRepository.findByIdForUpdate(assignedStudent.getId())
                .orElseThrow(() -> TaskHubException.notFound("Assigned student not found"));
        BigDecimal releaseAmount = funded.stream()
                .map(com.taskhub.entity.Milestone::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        funded.forEach(m -> {
            m.setEscrowStatus(EscrowStatus.RELEASED);
            m.setStatus(com.taskhub.entity.Milestone.MilestoneStatus.APPROVED);
            m.setReleasedAt(LocalDateTime.now());
        });
        milestoneRepository.saveAll(funded);
        student.setWalletBalance(student.getWalletBalance().add(releaseAmount));
        userRepository.save(student);
        walletService.recordTransaction(student, WalletTransactionType.escrow_release, releaseAmount, task);

        task.getAcceptanceCriteria().forEach(c -> c.setStatus(CriteriaStatus.PASSED));
        if (task.getStatus() != TaskStatus.COMPLETED) {
            taskService.transition(task, TaskStatus.COMPLETED);
        }
        taskRepository.save(task);
    }

}

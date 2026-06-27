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
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taskhub.util.EscrowCalculator;
import java.math.BigDecimal;

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

    /**
     * Kiểm tra escrow của task có đang ở trạng thái FUNDED hay không.
     */
    @Transactional(readOnly = true)
    public boolean isEscrowFunded(Long taskId) {
        return escrowRepository.findByTaskId(taskId)
                .map(escrow -> escrow.getStatus() == EscrowStatus.FUNDED)
                .orElse(true);
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

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        taskService.validateTransition(task, TaskStatus.ESCROW_FUNDED);

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

        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseGet(() -> Escrow.builder().task(task).build());
        if (escrow.getStatus() == EscrowStatus.FUNDED)
            throw TaskHubException.badRequest("Escrow already funded");
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
        // Keep task completion and wallet release on the same path.
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can release escrow");

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");

        Escrow escrow = escrowRepository.findByTaskId(taskId)
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

        User student = task.getAssignedTo();
        if (student == null)
            throw TaskHubException.badRequest("Task has no assigned student");

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
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can refund escrow");

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        taskService.validateTransition(task, TaskStatus.LOCKED);

        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Escrow not found"));
        if (escrow.getStatus() != EscrowStatus.FUNDED)
            throw TaskHubException.badRequest("Escrow not in FUNDED state");

        // Trả lại tiền cho hirer và ghi ledger refund.
        hirer.setWalletBalance(hirer.getWalletBalance().add(escrow.getAmount()));
        userRepository.save(hirer);
        walletService.recordTransaction(hirer, WalletTransactionType.refund, escrow.getAmount(), task);

        escrow.setStatus(EscrowStatus.REFUNDED);
        escrowRepository.save(escrow);

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
        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.DISPUTED)
            throw TaskHubException.badRequest("Task must be DISPUTED to request revision");

        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Escrow not found"));
        if (escrow.getStatus() != EscrowStatus.FUNDED)
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

}

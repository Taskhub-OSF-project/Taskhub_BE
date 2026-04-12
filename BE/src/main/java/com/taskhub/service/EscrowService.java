package com.taskhub.service;

import com.taskhub.entity.*;
import com.taskhub.enums.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EscrowService {
    private final EscrowRepository escrowRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;

    @Transactional
    public void fundEscrow(UUID taskId) {
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can fund escrow");

        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        if (task.getStatus() != TaskStatus.LOCKED)
            throw TaskHubException.badRequest("Task must be LOCKED before funding escrow");
        if (hirer.getWalletBalance().compareTo(task.getBudget()) < 0)
            throw TaskHubException.badRequest("Insufficient wallet balance");

        // Deduct from wallet
        hirer.setWalletBalance(hirer.getWalletBalance().subtract(task.getBudget()));
        userRepository.save(hirer);

        // Create escrow
        Escrow escrow = Escrow.builder().task(task).amount(task.getBudget()).status(EscrowStatus.FUNDED).build();
        escrowRepository.save(escrow);

        // Transition: LOCKED → ESCROW_FUNDED → ACTIVE
        task.setStatus(TaskStatus.ESCROW_FUNDED);
        taskRepository.save(task);
        task.setStatus(TaskStatus.ACTIVE);
        taskRepository.save(task);
    }

    @Transactional
    public void releaseEscrow(UUID taskId) {
        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.COMPLETED)
            throw TaskHubException.badRequest("Task must be COMPLETED to release escrow");

        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Escrow not found"));
        if (escrow.getStatus() != EscrowStatus.FUNDED)
            throw TaskHubException.badRequest("Escrow not in FUNDED state");

        User student = task.getAssignedTo();
        student.setWalletBalance(student.getWalletBalance().add(escrow.getAmount()));
        userRepository.save(student);

        escrow.setStatus(EscrowStatus.RELEASED);
        escrowRepository.save(escrow);
    }
}

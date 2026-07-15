package com.taskhub;

import com.taskhub.dto.request.CreateMilestoneRequest;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Milestone;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.EscrowStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.MilestoneRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.service.EscrowService;
import com.taskhub.service.MilestoneService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class Phase6MilestoneInvariantTests {
    @Autowired private MilestoneService milestoneService;
    @Autowired private EscrowService escrowService;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void totalMilestonesCannotExceedTaskBudget() {
        User hirer = user("phase6-limit@example.com", Role.HIRER, "5000");
        Task task = task(hirer, null, TaskStatus.DRAFT, "1000");
        authenticate(hirer);
        milestoneService.createMilestone(task.getId(), request("First", "700"));

        assertThrows(TaskHubException.class,
                () -> milestoneService.createMilestone(task.getId(), request("Second", "301")));
    }

    @Test
    void milestoneFundingIsExclusiveAndTransitionsOnlyAfterAllAreFunded() {
        User hirer = user("phase6-fund@example.com", Role.HIRER, "5000");
        Task task = task(hirer, null, TaskStatus.DRAFT, "1000");
        authenticate(hirer);
        var first = milestoneService.createMilestone(task.getId(), request("First", "400"));
        var second = milestoneService.createMilestone(task.getId(), request("Second", "600"));
        task.setStatus(TaskStatus.LOCKED);
        taskRepository.save(task);

        assertThrows(TaskHubException.class, () -> escrowService.fundEscrow(task.getId()));
        milestoneService.fundMilestone(task.getId(), first.getId());
        assertEquals(TaskStatus.LOCKED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
        milestoneService.fundMilestone(task.getId(), second.getId());

        assertEquals(TaskStatus.ESCROW_FUNDED,
                taskRepository.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("3950.00"),
                userRepository.findById(hirer.getId()).orElseThrow().getWalletBalance());
    }

    @Test
    void doubleFundDoesNotDeductTwice() {
        User hirer = user("phase6-double@example.com", Role.HIRER, "5000");
        Task task = task(hirer, null, TaskStatus.DRAFT, "1000");
        authenticate(hirer);
        var milestone = milestoneService.createMilestone(task.getId(), request("Only", "1000"));
        task.setStatus(TaskStatus.LOCKED);
        taskRepository.save(task);
        milestoneService.fundMilestone(task.getId(), milestone.getId());
        BigDecimal afterFirstFund = userRepository.findById(hirer.getId()).orElseThrow().getWalletBalance();

        assertThrows(TaskHubException.class,
                () -> milestoneService.fundMilestone(task.getId(), milestone.getId()));
        assertEquals(afterFirstFund,
                userRepository.findById(hirer.getId()).orElseThrow().getWalletBalance());
    }

    @Test
    void rejectRequestsRevisionWithoutRefundingEscrow() {
        User hirer = user("phase6-reject-hirer@example.com", Role.HIRER, "5000");
        User student = user("phase6-reject-student@example.com", Role.STUDENT, "0");
        Task task = task(hirer, student, TaskStatus.DRAFT, "1000");
        authenticate(hirer);
        var response = milestoneService.createMilestone(task.getId(), request("Only", "1000"));
        task.setStatus(TaskStatus.LOCKED);
        milestoneService.fundMilestone(task.getId(), response.getId());
        task.setStatus(TaskStatus.SUBMITTED);
        taskRepository.save(task);

        milestoneService.rejectMilestone(task.getId(), response.getId());
        Milestone milestone = milestoneRepository.findById(response.getId()).orElseThrow();

        assertEquals(EscrowStatus.FUNDED, milestone.getEscrowStatus());
        assertEquals(Milestone.MilestoneStatus.REJECTED, milestone.getStatus());
        assertEquals(TaskStatus.IN_PROGRESS, taskRepository.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("3950.00"),
                userRepository.findById(hirer.getId()).orElseThrow().getWalletBalance());
        assertEquals(new BigDecimal("0.00"),
                userRepository.findById(student.getId()).orElseThrow().getWalletBalance());
    }

    @Test
    void approveReleasesExactlyOnceAndCompletesTask() {
        User hirer = user("phase6-approve-hirer@example.com", Role.HIRER, "5000");
        User student = user("phase6-approve-student@example.com", Role.STUDENT, "0");
        Task task = task(hirer, student, TaskStatus.DRAFT, "1000");
        authenticate(hirer);
        var response = milestoneService.createMilestone(task.getId(), request("Only", "1000"));
        task.setStatus(TaskStatus.LOCKED);
        milestoneService.fundMilestone(task.getId(), response.getId());
        task.setStatus(TaskStatus.SUBMITTED);
        taskRepository.save(task);

        milestoneService.approveMilestone(task.getId(), response.getId());
        assertThrows(TaskHubException.class,
                () -> milestoneService.approveMilestone(task.getId(), response.getId()));

        assertEquals(new BigDecimal("1000.00"),
                userRepository.findById(student.getId()).orElseThrow().getWalletBalance());
        assertEquals(TaskStatus.COMPLETED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminRefundReturnsMilestonePrincipalAndFees() {
        User hirer = user("phase6-refund-hirer@example.com", Role.HIRER, "5000");
        User student = user("phase6-refund-student@example.com", Role.STUDENT, "0");
        User admin = user("phase6-refund-admin@example.com", Role.ADMIN, "0");
        Task task = task(hirer, student, TaskStatus.DRAFT, "1000");
        authenticate(hirer);
        var response = milestoneService.createMilestone(task.getId(), request("Only", "1000"));
        task.setStatus(TaskStatus.LOCKED);
        milestoneService.fundMilestone(task.getId(), response.getId());
        task.setStatus(TaskStatus.DISPUTED);
        taskRepository.save(task);
        authenticate(admin);

        escrowService.refundEscrow(task.getId());

        assertEquals(new BigDecimal("5000.00"),
                userRepository.findById(hirer.getId()).orElseThrow().getWalletBalance());
        assertEquals(EscrowStatus.REFUNDED,
                milestoneRepository.findById(response.getId()).orElseThrow().getEscrowStatus());
        assertEquals(TaskStatus.LOCKED, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    private CreateMilestoneRequest request(String title, String amount) {
        return CreateMilestoneRequest.builder()
                .title(title)
                .amount(new BigDecimal(amount))
                .dueDate(LocalDateTime.now().plusDays(1))
                .build();
    }

    private User user(String email, Role role, String balance) {
        return userRepository.save(User.builder()
                .email(email).password("encoded").fullName("Phase 6 User")
                .role(role).walletBalance(new BigDecimal(balance).setScale(2)).build());
    }

    private Task task(User hirer, User assigned, TaskStatus status, String budget) {
        Task task = Task.builder()
                .title("Phase 6 task")
                .description("Milestone invariant test")
                .budget(new BigDecimal(budget).setScale(2))
                .deadline(LocalDateTime.now().plusDays(3))
                .status(status).hirer(hirer).assignedTo(assigned).build();
        for (String description : List.of("First deliverable", "Second deliverable", "Final quality check")) {
            task.getAcceptanceCriteria().add(AcceptanceCriteria.builder()
                    .task(task).description(description).build());
        }
        return taskRepository.save(task);
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}

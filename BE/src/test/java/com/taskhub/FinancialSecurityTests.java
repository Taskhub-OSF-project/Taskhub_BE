package com.taskhub;

import com.taskhub.dto.request.ApplicationRequest;
import com.taskhub.entity.Task;
import com.taskhub.entity.TaskApplication;
import com.taskhub.entity.User;
import com.taskhub.enums.ApplicationStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.TaskApplicationRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.service.ApplicationService;
import com.taskhub.service.EscrowService;
import com.taskhub.service.WalletService;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class FinancialSecurityTests {
    @Autowired private WalletService walletService;
    @Autowired private ApplicationService applicationService;
    @Autowired private EscrowService escrowService;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskApplicationRepository applicationRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directCashSimulationIsDisabledByDefault() {
        authenticate(user("finance-wallet@example.com", Role.HIRER));
        assertThrows(TaskHubException.class, () -> walletService.deposit(BigDecimal.TEN));
        assertThrows(TaskHubException.class, () -> walletService.withdraw(BigDecimal.TEN));
    }

    @Test
    void expiredTaskCannotAcceptAPreviouslySubmittedApplication() {
        User hirer = user("finance-deadline-hirer@example.com", Role.HIRER);
        User student = user("finance-deadline-student@example.com", Role.STUDENT);
        Task task = taskRepository.save(task(hirer, null, LocalDateTime.now().minusMinutes(1)));
        TaskApplication application = applicationRepository.save(TaskApplication.builder()
                .task(task).student(student).status(ApplicationStatus.PENDING).build());
        authenticate(hirer);

        assertThrows(TaskHubException.class,
                () -> applicationService.acceptApplication(application.getId()));
    }

    @Test
    void taskOwnerCannotApplyToOwnTask() {
        User owner = user("finance-self@example.com", Role.STUDENT);
        Task task = taskRepository.save(task(owner, null, LocalDateTime.now().plusDays(1)));
        authenticate(owner);

        assertThrows(TaskHubException.class, () -> applicationService.apply(
                task.getId(), ApplicationRequest.builder().coverLetter("self").build()));
    }

    @Test
    void missingEscrowIsNeverReportedAsFunded() {
        User hirer = user("finance-escrow@example.com", Role.HIRER);
        Task task = taskRepository.save(task(hirer, null, LocalDateTime.now().plusDays(1)));
        assertFalse(escrowService.isEscrowFunded(task.getId()));
    }

    private User user(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email).password("encoded").fullName("Finance Test").role(role).build());
    }

    private Task task(User hirer, User assigned, LocalDateTime deadline) {
        return Task.builder()
                .title("Financial invariant")
                .description("Financial invariant test task")
                .budget(new BigDecimal("1000.00"))
                .deadline(deadline)
                .status(TaskStatus.ACTIVE)
                .hirer(hirer)
                .assignedTo(assigned)
                .build();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}

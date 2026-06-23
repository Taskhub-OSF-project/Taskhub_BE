package com.taskhub;

import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.Escrow;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.EscrowStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.enums.WalletTransactionType;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.EscrowRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.repository.WalletTransactionRepository;
import com.taskhub.service.EscrowService;
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
class EscrowReleaseTests {
    @Autowired private EscrowService escrowService;
    @Autowired private EscrowRepository escrowRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void releaseSubmittedTaskCompletesAndCreditsStudentWallet() {
        User hirer = createUser("release-hirer-1@example.com", Role.HIRER);
        User student = createUser("release-student-1@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.SUBMITTED, "70000");
        createFundedEscrow(task);
        setAuth(hirer);

        escrowService.releaseEscrow(task.getId());

        Task savedTask = taskRepository.findById(task.getId()).orElseThrow();
        User savedStudent = userRepository.findById(student.getId()).orElseThrow();
        var transactions = walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(student.getId());

        assertEquals(TaskStatus.COMPLETED, savedTask.getStatus());
        assertMoneyEquals("70000", savedStudent.getWalletBalance());
        assertEquals(1, transactions.size());
        assertEquals(WalletTransactionType.escrow_release, transactions.get(0).getType());
        assertMoneyEquals("70000", transactions.get(0).getAmount());
    }

    @Test
    void releaseAlreadyReleasedEscrowDoesNotCreditTwice() {
        User hirer = createUser("release-hirer-2@example.com", Role.HIRER);
        User student = createUser("release-student-2@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.SUBMITTED, "50000");
        createFundedEscrow(task);
        setAuth(hirer);

        escrowService.releaseEscrow(task.getId());
        escrowService.releaseEscrow(task.getId());

        User savedStudent = userRepository.findById(student.getId()).orElseThrow();
        var transactions = walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(student.getId());

        assertMoneyEquals("50000", savedStudent.getWalletBalance());
        assertEquals(1, transactions.size());
    }

    @Test
    void nonOwnerCannotReleaseEscrow() {
        User hirer = createUser("release-hirer-3@example.com", Role.HIRER);
        User otherHirer = createUser("release-hirer-3b@example.com", Role.HIRER);
        User student = createUser("release-student-3@example.com", Role.STUDENT);
        Task task = createTask(hirer, student, TaskStatus.SUBMITTED, "30000");
        createFundedEscrow(task);
        setAuth(otherHirer);

        assertThrows(TaskHubException.class, () -> escrowService.releaseEscrow(task.getId()));
    }

    private void setAuth(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password("encoded")
                .fullName("Test User")
                .role(role)
                .build());
    }

    private Task createTask(User hirer, User student, TaskStatus status, String budget) {
        Task task = Task.builder()
                .title("Release escrow task")
                .description("Deliver task and release escrow")
                .budget(new BigDecimal(budget))
                .deadline(LocalDateTime.now().plusDays(3))
                .status(status)
                .hirer(hirer)
                .assignedTo(student)
                .build();
        task.getAcceptanceCriteria().add(AcceptanceCriteria.builder()
                .description("Deliver 1 ZIP file")
                .task(task)
                .build());
        return taskRepository.save(task);
    }

    private Escrow createFundedEscrow(Task task) {
        return escrowRepository.save(Escrow.builder()
                .task(task)
                .amount(task.getBudget())
                .platformFee(BigDecimal.ZERO)
                .status(EscrowStatus.FUNDED)
                .build());
    }
}

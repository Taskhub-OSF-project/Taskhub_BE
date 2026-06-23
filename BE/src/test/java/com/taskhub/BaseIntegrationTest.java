package com.taskhub;

import com.taskhub.config.TestConfig;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.repository.TaskRepository;
import com.taskhub.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Base integration test class. All integration tests should extend this.
 * Provides shared helper methods and imports TestConfig (mocks EmailService/AuditService).
 */
@SpringBootTest
@Transactional
@Import(TestConfig.class)
public abstract class BaseIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Auth helpers ───────────────────────────────────────

    protected void setAuth(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    protected void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ── Factory helpers ────────────────────────────────────

    protected User createUser(Role role) {
        return createUser(randomEmail(), role);
    }

    protected User createUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password("{bcrypt}$2a$10$dummyEncodedPassword")   // never checked in unit-of-work
                .fullName("Test User " + email)
                .role(role)
                .isVerified(true)
                .build());
    }

    protected User createVerifiedUser(String email, Role role) {
        User user = createUser(email, role);
        user.setIsVerified(true);
        return userRepository.save(user);
    }

    protected Task createTask(User hirer, TaskStatus status) {
        // Fund wallet: TaskService requires hirer to have budget + 5% platform fee
        fundWallet(hirer, new BigDecimal("2000.00"));

        Task task = Task.builder()
                .title("Sample task — " + UUID.randomUUID())
                .description("Deliver 1 PNG file, 1920×1080 px, max 5 MB")
                .budget(new BigDecimal("1000.00"))
                .deadline(LocalDateTime.now().plusDays(7))
                .status(status)
                .hirer(hirer)
                .build();
        task = taskRepository.save(task);
        // lockTask requires acceptance criteria to pass AI validation
        task.setAcceptanceCriteria(new java.util.ArrayList<>(List.of(
                com.taskhub.entity.AcceptanceCriteria.builder()
                        .task(task).description("Submit final deliverable file").build(),
                com.taskhub.entity.AcceptanceCriteria.builder()
                        .task(task).description("Provide source files in ZIP format").build()
        )));
        return taskRepository.save(task);
    }

    protected void fundWallet(User user, BigDecimal amount) {
        // Use EntityManager.merge so the L1 cache reflects the updated balance
        user.setWalletBalance(amount);
        entityManager.merge(user);
        entityManager.flush();
    }

    protected Task createDraftTask(User hirer) {
        return createTask(hirer, TaskStatus.DRAFT);
    }

    protected Task createActiveTask(User hirer) {
        return createTask(hirer, TaskStatus.ACTIVE);
    }

    protected String randomEmail() {
        return UUID.randomUUID() + "@test.taskhub";
    }
}

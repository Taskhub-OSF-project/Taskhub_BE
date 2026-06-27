package com.taskhub.service;

import com.taskhub.BaseIntegrationTest;
import com.taskhub.dto.request.CreateTaskRequest;
import com.taskhub.dto.request.PatchTaskRequest;
import com.taskhub.dto.response.TaskResponse;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.TaskApplicationRepository;
import com.taskhub.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskServiceTest extends BaseIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskApplicationRepository taskApplicationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ── createTask ──────────────────────────────────────────

    @Test
    void createTask_AsHirer_Success() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        // Fund wallet via entityManager so the loaded entity is updated
        hirer.setWalletBalance(new BigDecimal("5000.00"));
        entityManager.merge(hirer);
        entityManager.flush();

        CreateTaskRequest req = CreateTaskRequest.builder()
                .title("Sample task")
                .description("Deliver 1 PNG file, 1920x1080 px, max 5 MB")
                .category("Design")
                .budget(new BigDecimal("1000.00"))
                .deadline(LocalDateTime.now().plusDays(14))
                .acceptanceCriteria(java.util.List.of(
                        "Create 1 PNG logo in 1920x1080 pixels",
                        "Use maximum 3 colors in design",
                        "Deliver SVG vector assets source files"))
                .build();

        TaskResponse resp = taskService.createTask(req);

        assertNotNull(resp.getId());
        assertEquals("Sample task", resp.getTitle());
        assertEquals(TaskStatus.DRAFT, resp.getStatus());
        assertEquals(hirer.getId(), resp.getHirerId());
    }

    @Test
    void createTask_AsStudent_Forbidden() {
        User student = createUser(Role.STUDENT);
        setAuth(student);

        CreateTaskRequest req = CreateTaskRequest.builder()
                .title("Task").description("Desc")
                .budget(new BigDecimal("100")).deadline(LocalDateTime.now().plusDays(3))
                .acceptanceCriteria(java.util.List.of(
                        "Build functional code",
                        "Deliver source package files",
                        "Clean code with comments"))
                .build();

        assertThrows(TaskHubException.class, () -> taskService.createTask(req));
    }

    // ── updateTask ───────────────────────────────────────────

    @Test
    void updateTask_AsOwner_Success() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createDraftTask(hirer);

        PatchTaskRequest req = PatchTaskRequest.builder()
                .title("Updated title")
                .description("Updated description")
                .budget(new BigDecimal("2000"))
                .build();

        TaskResponse resp = taskService.updateTask(task.getId(), req);

        assertEquals("Updated title", resp.getTitle());
        assertEquals("Updated description", resp.getDescription());
    }

    @Test
    void updateTask_NonDraft_Fails() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createActiveTask(hirer);

        PatchTaskRequest req = PatchTaskRequest.builder().title("New title").build();

        assertThrows(TaskHubException.class, () -> taskService.updateTask(task.getId(), req));
    }

    @Test
    void updateTask_NotOwner_Forbidden() {
        User hirer = createUser(Role.HIRER);
        User other = createUser(Role.HIRER);
        setAuth(other);
        Task task = createDraftTask(hirer);

        PatchTaskRequest req = PatchTaskRequest.builder().title("Hack").build();

        assertThrows(TaskHubException.class, () -> taskService.updateTask(task.getId(), req));
    }

    // ── deleteTask ──────────────────────────────────────────

    @Test
    void deleteTask_DraftWithNoApplications_Success() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createDraftTask(hirer);

        taskService.deleteTask(task.getId());

        assertTrue(taskRepository.findById(task.getId()).isEmpty());
    }

    @Test
    void deleteTask_HasApplications_Fails() {
        User hirer = createUser(Role.HIRER);
        User student = createUser(Role.STUDENT);
        setAuth(hirer);
        Task task = createDraftTask(hirer);

        taskApplicationRepository.save(
                com.taskhub.entity.TaskApplication.builder().task(task).student(student).build());

        assertThrows(TaskHubException.class, () -> taskService.deleteTask(task.getId()));
    }

    @Test
    void deleteTask_NonOwner_Forbidden() {
        User hirer = createUser(Role.HIRER);
        setAuth(createUser(Role.HIRER));
        Task task = createDraftTask(hirer);

        assertThrows(TaskHubException.class, () -> taskService.deleteTask(task.getId()));
    }

    // ── publishTask ─────────────────────────────────────────
    // State machine: DRAFT -> canTransitionTo LOCKED only.
    // publishTask opens a task for applications after escrow is funded.

    @Test
    void publishTask_FromEscrowFunded_SetsToActive() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createDraftTask(hirer);
        taskService.transitionTask(task.getId(), TaskStatus.LOCKED);
        taskService.transitionTask(task.getId(), TaskStatus.ESCROW_FUNDED);

        TaskResponse resp = taskService.publishTask(task.getId());

        assertEquals(TaskStatus.ACTIVE, resp.getStatus());
    }

    @Test
    void publishTask_FromDraft_Fails() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createDraftTask(hirer);

        assertThrows(TaskHubException.class, () -> taskService.publishTask(task.getId()));
    }

    @Test
    void publishTask_FromActive_Fails() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createActiveTask(hirer);

        assertThrows(TaskHubException.class, () -> taskService.publishTask(task.getId()));
    }

    // ── disputeTask ──────────────────────────────────────────
    // SUBMITTED tasks can transition to DISPUTED.

    @Test
    void disputeTask_FromSubmitted_Success() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createActiveTask(hirer);

        taskService.transitionTask(task.getId(), TaskStatus.IN_PROGRESS);
        taskService.transitionTask(task.getId(), TaskStatus.SUBMITTED);
        TaskResponse resp = taskService.disputeTask(task.getId());

        assertEquals(TaskStatus.DISPUTED, resp.getStatus());
    }

    @Test
    void disputeTask_FromDraft_Fails() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createDraftTask(hirer);

        assertThrows(TaskHubException.class, () -> taskService.disputeTask(task.getId()));
    }

    // ── getTask ─────────────────────────────────────────────

    @Test
    void getTask_ExistingTask_ReturnsTask() {
        User hirer = createUser(Role.HIRER);
        setAuth(hirer);
        Task task = createActiveTask(hirer);

        TaskResponse resp = taskService.getTask(task.getId());

        assertNotNull(resp.getId());
        assertEquals(task.getId(), resp.getId());
    }

    @Test
    void getTask_NonExisting_Throws() {
        assertThrows(TaskHubException.class, () -> taskService.getTask(99999L));
    }
}

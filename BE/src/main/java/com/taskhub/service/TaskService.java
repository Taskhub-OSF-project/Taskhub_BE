package com.taskhub.service;

import com.taskhub.dto.request.CreateTaskRequest;
import com.taskhub.dto.request.RevisionRequest;
import com.taskhub.dto.response.*;
import com.taskhub.entity.*;
import com.taskhub.enums.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j // Add proper logger
public class TaskService {
    private final TaskRepository taskRepository;
    private final AiValidationService aiValidation;
    // Remove unused criteriaRepository

    @Transactional
    public TaskResponse createTask(CreateTaskRequest req) {
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can create tasks");

        Task task = Task.builder()
                .title(req.getTitle()).description(req.getDescription())
                .budget(req.getBudget()).deadline(req.getDeadline())
                .hirer(hirer).status(TaskStatus.DRAFT).build();

        for (String desc : req.getAcceptanceCriteria()) {
            task.getAcceptanceCriteria().add(
                    AcceptanceCriteria.builder().description(desc).task(task).build());
        }
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse getTask(UUID id) {
        return toResponse(findTask(id));
    }

    public List<TaskResponse> getMyTasks() {
        User user = AuthUtil.getCurrentUser();
        List<Task> tasks = user.getRole() == Role.HIRER
                ? taskRepository.findByHirerId(user.getId())
                : taskRepository.findByAssignedToId(user.getId());
        return tasks.stream().map(this::toResponse).toList();
    }

    public List<TaskResponse> getAvailableTasks() {
        return taskRepository.findByStatusIn(List.of(TaskStatus.ACTIVE))
                .stream().map(this::toResponse).toList();
    }

    // SINGLE lockTask method - enhanced version only
    @Transactional
    public TaskResponse lockTask(UUID taskId) {
        Task task = findOwnedTask(taskId);
        validateTransition(task, TaskStatus.LOCKED);

        List<String> criteriaDescs = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription).toList();

        // Enhanced AI validation
        var result = aiValidation.validateCriteriaEnhanced(criteriaDescs);
        if (!result.valid()) {
            // Log validation failure with proper logger
            log.warn("Task lock failed for task {}: {}", taskId, result.message());
            throw TaskHubException.badRequest("Cannot lock: " + result.message());
        }

        task.setStatus(TaskStatus.LOCKED);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse transitionTask(UUID taskId, TaskStatus newStatus) {
        Task task = findTask(taskId);
        validateTransition(task, newStatus);
        task.setStatus(newStatus);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse requestRevision(UUID taskId, RevisionRequest req) {
        Task task = findOwnedTask(taskId);
        if (task.getStatus() != TaskStatus.SUBMITTED)
            throw TaskHubException.badRequest("Can only request revision on submitted tasks");

        for (AcceptanceCriteria c : task.getAcceptanceCriteria()) {
            if (req.getFailedCriteriaIds().contains(c.getId())) {
                c.setStatus(CriteriaStatus.FAILED);
            }
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse disputeTask(UUID taskId) {
        Task task = findTask(taskId);
        validateTransition(task, TaskStatus.DISPUTED);
        task.setStatus(TaskStatus.DISPUTED);
        return toResponse(taskRepository.save(task));
    }

    // ===== Public helpers =====

    public Task findTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
    }

    public Task findOwnedTask(UUID id) {
        Task task = findTask(id);
        if (!task.getHirer().getId().equals(AuthUtil.getCurrentUser().getId()))
            throw TaskHubException.forbidden("Not your task");
        return task;
    }

    // ===== Private helpers =====

    private void validateTransition(Task task, TaskStatus next) {
        if (!task.getStatus().canTransitionTo(next))
            throw TaskHubException.badRequest(
                    "Invalid transition: " + task.getStatus() + " → " + next);
    }

    TaskResponse toResponse(Task t) {
        return TaskResponse.builder()
                .id(t.getId()).title(t.getTitle()).description(t.getDescription())
                .budget(t.getBudget()).deadline(t.getDeadline()).status(t.getStatus())
                .hirerId(t.getHirer().getId()).hirerName(t.getHirer().getFullName())
                .assignedToId(t.getAssignedTo() != null ? t.getAssignedTo().getId() : null)
                .assignedToName(t.getAssignedTo() != null ? t.getAssignedTo().getFullName() : null)
                .acceptanceCriteria(t.getAcceptanceCriteria().stream().map(c ->
                        CriteriaResponse.builder().id(c.getId())
                                .description(c.getDescription()).status(c.getStatus()).build()
                ).toList())
                .createdAt(t.getCreatedAt()).build();
    }
}

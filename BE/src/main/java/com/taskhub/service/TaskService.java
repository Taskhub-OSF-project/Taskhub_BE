package com.taskhub.service;

import com.taskhub.dto.request.CreateTaskRequest;
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

@Service
@RequiredArgsConstructor
@Slf4j // Add proper logger
public class TaskService {
    private final TaskRepository taskRepository;
    private final AiValidationService aiValidation;
    private final WalletService walletService;
    private final TaskApplicationRepository appRepository;

    @Transactional
    public TaskResponse createTask(CreateTaskRequest req) {
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can create tasks");

        walletService.requireSufficientForCreateTask(req.getBudget());
        List<String> criteria = normalizeCriteria(req.getAcceptanceCriteria());
        requireValidCriteria(criteria);

        Task task = Task.builder()
                .title(req.getTitle()).description(req.getDescription())
                .category(trimToNull(req.getCategory()))
                .budget(req.getBudget()).deadline(req.getDeadline())
                .hirer(hirer).status(TaskStatus.DRAFT).build();

        for (String desc : criteria) {
            task.getAcceptanceCriteria().add(
                    AcceptanceCriteria.builder().description(desc).task(task).build());
        }
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse getTask(Long id) {
        Task task = findTask(id);
        User user = AuthUtil.getCurrentUser();
        boolean includeApplicants = user.getRole() == Role.HIRER &&
                task.getHirer().getId().equals(user.getId());
        return toResponse(task, includeApplicants);
    }

    public List<TaskResponse> getMyTasks(String status) {
        User user = AuthUtil.getCurrentUser();
        TaskStatus filter = parseStatus(status);
        boolean isHirer = user.getRole() == Role.HIRER;

        List<Task> tasks = isHirer
                ? (filter == null ? taskRepository.findByHirerId(user.getId())
                : taskRepository.findByHirerIdAndStatus(user.getId(), filter))
                : (filter == null ? taskRepository.findByAssignedToId(user.getId())
                : taskRepository.findByAssignedToIdAndStatus(user.getId(), filter));

        return tasks.stream()
                .map(t -> toResponse(t, isHirer))
                .toList();
    }

    public List<TaskResponse> getAvailableTasks() {
        return taskRepository.findByStatusIn(List.of(TaskStatus.ACTIVE))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, com.taskhub.dto.request.PatchTaskRequest req) {
        User user = AuthUtil.getCurrentUser();
        if (user.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can update tasks");

        Task task = findOwnedTask(taskId);
        if (task.getStatus() != TaskStatus.DRAFT)
            throw TaskHubException.badRequest("Only DRAFT tasks can be updated");

        if (req.getTitle() != null) {
            String title = req.getTitle().trim();
            if (title.isEmpty()) throw TaskHubException.badRequest("Title cannot be blank");
            task.setTitle(title);
        }
        if (req.getDescription() != null) {
            String description = req.getDescription().trim();
            if (description.isEmpty()) throw TaskHubException.badRequest("Description cannot be blank");
            task.setDescription(description);
        }
        if (req.getBudget() != null) task.setBudget(req.getBudget());
        if (req.getDeadline() != null) task.setDeadline(req.getDeadline());
        if (req.getCategory() != null) task.setCategory(trimToNull(req.getCategory()));

        if (req.getAcceptanceCriteria() != null) {
            List<String> criteria = normalizeCriteria(req.getAcceptanceCriteria());
            requireValidCriteria(criteria);
            task.getAcceptanceCriteria().clear();
            for (String desc : criteria) {
                task.getAcceptanceCriteria().add(
                        AcceptanceCriteria.builder().description(desc).task(task).build());
            }
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(Long taskId) {
        User user = AuthUtil.getCurrentUser();
        if (user.getRole() != Role.HIRER)
            throw TaskHubException.forbidden("Only hirers can delete tasks");

        Task task = findOwnedTask(taskId);
        if (task.getStatus() != TaskStatus.DRAFT)
            throw TaskHubException.badRequest("Only DRAFT tasks can be deleted");
        if (appRepository.existsByTaskId(taskId))
            throw TaskHubException.badRequest("Cannot delete task with existing applications");

        taskRepository.delete(task);
    }

    // SINGLE lockTask method - enhanced version only
    @Transactional
    public TaskResponse lockTask(Long taskId) {
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

        transition(task, TaskStatus.LOCKED);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse transitionTask(Long taskId, TaskStatus newStatus) {
        Task task = findTask(taskId);
        transition(task, newStatus);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse publishTask(Long taskId) {
        Task task = findOwnedTask(taskId);
        transition(task, TaskStatus.ACTIVE);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse disputeTask(Long taskId) {
        Task task = findTask(taskId);
        transition(task, TaskStatus.DISPUTED);
        return toResponse(taskRepository.save(task));
    }

    // ===== Public helpers =====

    public Task findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
    }

    public Task findOwnedTask(Long id) {
        Task task = findTask(id);
        if (!task.getHirer().getId().equals(AuthUtil.getCurrentUser().getId()))
            throw TaskHubException.forbidden("Not your task");
        return task;
    }

    public void transition(Task task, TaskStatus next) {
        validateTransition(task, next);
        task.setStatus(next);
    }

    // ===== Private helpers =====

    public void validateTransition(Task task, TaskStatus next) {
        if (!task.getStatus().canTransitionTo(next))
            throw TaskHubException.badRequest(
                    "Invalid transition: " + task.getStatus() + " -> " + next);
    }

    public AiValidationService.ValidationResult validateCriteriaList(List<String> criteria) {
        return aiValidation.validateCriteriaEnhanced(criteria);
    }

    public void requireValidCriteria(List<String> criteria) {
        var result = aiValidation.validateCriteriaEnhanced(criteria);
        if (!result.valid()) {
            throw TaskHubException.invalidCriteria(result.message(), result);
        }
    }

    TaskResponse toResponse(Task t) {
        return toResponse(t, false);
    }

    TaskResponse toResponse(Task t, boolean includeApplicants) {
        return TaskResponse.builder()
                .id(t.getId()).title(t.getTitle()).description(t.getDescription())
                .category(t.getCategory())
                .budget(t.getBudget()).deadline(t.getDeadline()).status(t.getStatus())
                .hirerId(t.getHirer().getId()).hirerName(t.getHirer().getFullName())
                .assignedToId(t.getAssignedTo() != null ? t.getAssignedTo().getId() : null)
                .assignedToName(t.getAssignedTo() != null ? t.getAssignedTo().getFullName() : null)
                .revisionCount(t.getRevisionCount() != null ? t.getRevisionCount() : 0)
                .acceptanceCriteria(t.getAcceptanceCriteria().stream().map(c ->
                        CriteriaResponse.builder().id(c.getId())
                                .description(c.getDescription()).status(c.getStatus()).build()
                ).toList())
                .applicants(includeApplicants ? appRepository.findByTaskId(t.getId()).stream()
                        .map(this::toApplicationResponse).toList() : null)
                .createdAt(t.getCreatedAt()).build();
    }

    private ApplicationResponse toApplicationResponse(TaskApplication a) {
        return ApplicationResponse.builder()
                .id(a.getId()).taskId(a.getTask().getId())
                .studentId(a.getStudent().getId()).studentName(a.getStudent().getFullName())
                .studentUniversity(a.getStudent().getUniversity())
                .studentMajor(a.getStudent().getMajor())
                .coverLetter(a.getCoverLetter()).status(a.getStatus()).appliedAt(a.getAppliedAt()).build();
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw TaskHubException.badRequest("Invalid status: " + status);
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeCriteria(List<String> criteria) {
        if (criteria == null || criteria.isEmpty())
            throw TaskHubException.badRequest("Acceptance criteria cannot be empty");
        for (String c : criteria) {
            if (c == null || c.trim().isEmpty())
                throw TaskHubException.badRequest("Acceptance criteria cannot be blank");
        }
        return criteria.stream().map(String::trim).toList();
    }
}

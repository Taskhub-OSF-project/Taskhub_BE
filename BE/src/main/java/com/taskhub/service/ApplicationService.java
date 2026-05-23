package com.taskhub.service;

import com.taskhub.dto.request.ApplicationRequest;
import com.taskhub.dto.response.ApplicationResponse;
import com.taskhub.entity.*;
import com.taskhub.enums.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final TaskApplicationRepository appRepo;
    private final TaskRepository taskRepo;
    private final TaskService taskService;

    @Transactional
    public ApplicationResponse apply(UUID taskId, ApplicationRequest req) {
        User student = AuthUtil.getCurrentUser();
        if (student.getRole() != Role.STUDENT)
            throw TaskHubException.forbidden("Only students can apply");

        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.ACTIVE)
            throw TaskHubException.badRequest("Task is not accepting applications");
        if (appRepo.existsByTaskIdAndStudentId(taskId, student.getId()))
            throw TaskHubException.badRequest("Already applied");

        TaskApplication app = TaskApplication.builder()
                .task(task).student(student).coverLetter(req.getCoverLetter()).build();
        return toResponse(appRepo.save(app));
    }

    @Transactional
    public void acceptApplication(UUID applicationId) {
        User hirer = AuthUtil.getCurrentUser();
        TaskApplication app = appRepo.findById(applicationId)
                .orElseThrow(() -> TaskHubException.notFound("Application not found"));
        Task task = app.getTask();

        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        if (task.getStatus() != TaskStatus.ACTIVE)
            throw TaskHubException.badRequest("Task is not ACTIVE");

        app.setStatus(ApplicationStatus.ACCEPTED);
        appRepo.save(app);

        task.setAssignedTo(app.getStudent());
        taskService.transition(task, TaskStatus.IN_PROGRESS);
        taskRepo.save(task);

        // Reject other applications
        appRepo.findByTaskId(task.getId()).stream()
                .filter(a -> !a.getId().equals(applicationId))
                .forEach(a -> { a.setStatus(ApplicationStatus.REJECTED); appRepo.save(a); });
    }

    public List<ApplicationResponse> getTaskApplications(UUID taskId) {
        return appRepo.findByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    public List<ApplicationResponse> getMyApplications() {
        return appRepo.findByStudentId(AuthUtil.getCurrentUser().getId())
                .stream().map(this::toResponse).toList();
    }

    private ApplicationResponse toResponse(TaskApplication a) {
        return ApplicationResponse.builder()
                .id(a.getId()).taskId(a.getTask().getId())
                .studentId(a.getStudent().getId()).studentName(a.getStudent().getFullName())
                .coverLetter(a.getCoverLetter()).status(a.getStatus()).appliedAt(a.getAppliedAt()).build();
    }
}

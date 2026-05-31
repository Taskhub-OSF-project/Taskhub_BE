package com.taskhub.service;

import com.taskhub.dto.request.SubmissionRequest;
import com.taskhub.dto.response.SubmissionResponse;
import com.taskhub.entity.*;
import com.taskhub.enums.*;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Service xử lý nghiệp vụ nộp bài và duyệt bài.
 * Thuộc module Submission, được gọi từ SubmissionController.
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionRepository submissionRepo;
    private final TaskRepository taskRepo;
    private final AiValidationService aiValidation;
    private final TaskService taskService;
    private final EscrowService escrowService;

    /**
     * Student nộp bài cho task đang IN_PROGRESS.
     * Rule: chỉ assignee được nộp, chấm điểm heuristic.
     */
    @Transactional
    public SubmissionResponse submit(Long taskId, SubmissionRequest req) {
        User student = AuthUtil.getCurrentUser();
        // Chỉ STUDENT được submit.
        if (student.getRole() != Role.STUDENT)
            throw TaskHubException.forbidden("Only students can submit");

        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.IN_PROGRESS)
            throw TaskHubException.badRequest("Task is not IN_PROGRESS");
        if (!task.getAssignedTo().getId().equals(student.getId()))
            throw TaskHubException.forbidden("Not assigned to you");

        // Chỉ đánh giá criteria chưa PASSED.
        List<String> criteriaToEvaluate = task.getAcceptanceCriteria().stream()
                .filter(c -> c.getStatus() != CriteriaStatus.PASSED)
                .map(AcceptanceCriteria::getDescription).toList();

        int score = aiValidation.scoreSubmission(req.getNotes(), criteriaToEvaluate);

        if (score == 0)
            throw TaskHubException.badRequest("Submission score is 0%. Cannot submit. Please review requirements.");

        boolean isRevision = submissionRepo.findByTaskId(taskId).size() > 0;

        Submission submission = Submission.builder()
                .task(task).student(student).fileUrl(req.getFileUrl()).notes(req.getNotes())
                .aiScore(score).isRevision(isRevision)
                .aiReport(score < 70 ? "Warning: Score below 70%. Submission allowed but may need revision." : "Submission meets criteria.")
                .build();
        submissionRepo.save(submission);

        taskService.transition(task, TaskStatus.SUBMITTED);
        taskRepo.save(task);

        return toResponse(submission);
    }

    /**
     * Hirer duyệt bài: mark criteria PASSED, task COMPLETED, release escrow.
     */
    @Transactional
    public void approveSubmission(Long taskId) {
        User hirer = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId()))
            throw TaskHubException.forbidden("Not your task");
        if (task.getStatus() != TaskStatus.SUBMITTED)
            throw TaskHubException.badRequest("Task is not SUBMITTED");

        // Duyệt toàn bộ criteria và chuyển trạng thái task.
        task.getAcceptanceCriteria().forEach(c -> c.setStatus(CriteriaStatus.PASSED));
        taskService.transition(task, TaskStatus.COMPLETED);
        taskRepo.save(task);
        escrowService.releaseEscrow(taskId);
    }

    /**
     * Lấy lịch sử submission theo task.
     */
    public List<SubmissionResponse> getTaskSubmissions(Long taskId) {
        return submissionRepo.findByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    /**
     * Tạo báo cáo tranh chấp dạng text từ notes và criteria.
     */
    public String generateDisputeReport(Long taskId) {
        Task task = taskService.findTask(taskId);
        List<Submission> subs = submissionRepo.findByTaskId(taskId);
        if (subs.isEmpty()) throw TaskHubException.badRequest("No submissions found");

        Submission latest = subs.get(subs.size() - 1);
        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription).toList();
        return aiValidation.generateDisputeReport(latest.getNotes(), criteria);
    }

    private SubmissionResponse toResponse(Submission s) {
        // Mapping entity -> DTO để trả về client.
        return SubmissionResponse.builder()
                .id(s.getId()).taskId(s.getTask().getId())
                .studentId(s.getStudent().getId()).studentName(s.getStudent().getFullName())
                .fileUrl(s.getFileUrl()).notes(s.getNotes())
                .aiScore(s.getAiScore()).aiReport(s.getAiReport())
                .isRevision(s.getIsRevision()).submittedAt(s.getSubmittedAt()).build();
    }
}

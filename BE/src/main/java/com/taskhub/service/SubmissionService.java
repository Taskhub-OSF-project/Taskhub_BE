package com.taskhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.SubmittedFileDto;
import com.taskhub.dto.request.RevisionRequest;
import com.taskhub.dto.request.SubmissionRequest;
import com.taskhub.dto.response.CriteriaAIResult;
import com.taskhub.dto.response.LatestSubmissionResultResponse;
import com.taskhub.dto.response.RevisionRequestResponse;
import com.taskhub.dto.response.RevisionSuggestionResponse;
import com.taskhub.dto.response.SubmissionAIResult;
import com.taskhub.dto.response.SubmissionResponse;
import com.taskhub.entity.AcceptanceCriteria;
import com.taskhub.entity.EvaluationRecord;
import com.taskhub.entity.Submission;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.CriteriaStatus;
import com.taskhub.enums.Role;
import com.taskhub.enums.TaskStatus;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.EvaluationRecordRepository;
import com.taskhub.repository.RevisionRequestRepository;
import com.taskhub.repository.SubmissionRepository;
import com.taskhub.repository.TaskRepository;
import com.taskhub.security.AuthUtil;
import com.taskhub.util.FileUploadValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {
    private final SubmissionRepository submissionRepo;
    private final RevisionRequestRepository revisionRequestRepo;
    private final TaskRepository taskRepo;
    private final AiValidationService aiValidation;
    private final EvaluationRecordRepository evaluationRecordRepository;
    private final TaskService taskService;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SubmissionAIResult precheck(Long taskId, SubmissionRequest req) {
        if (req == null) {
            throw TaskHubException.badRequest("Request body is required");
        }
        User student = AuthUtil.getCurrentUser();
        if (student.getRole() != Role.STUDENT) {
            throw TaskHubException.forbidden("Only students can precheck submissions");
        }

        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw TaskHubException.badRequest("Task is not IN_PROGRESS");
        }
        if (!escrowService.isEscrowFunded(taskId)) {
            throw TaskHubException.badRequest("Escrow is not funded for this task");
        }
        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(student.getId())) {
            throw TaskHubException.forbidden("Not assigned to you");
        }

        // For precheck, accept either full submittedFiles list or a simple fileUrl/notes.
        // We only need file metadata for AI keyword matching, so path validation is skipped here.
        List<SubmittedFileDto> submittedFiles = buildPrecheckFiles(req);
        boolean hasContent = !submittedFiles.isEmpty()
                || (req.getNotes() != null && !req.getNotes().isBlank())
                || (req.getFileUrl() != null && !req.getFileUrl().isBlank());
        if (!hasContent) {
            throw TaskHubException.badRequest("At least one of submittedFiles, fileUrl, or notes is required for precheck");
        }

        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription)
                .toList();
        SubmissionAIResult result = aiValidation.evaluateSubmissionPrecheck(req.getNotes(), submittedFiles, criteria);

        task.setSubmissionAIResultJson(toJson(result));
        task.setLatestPrecheckAt(result.getEvaluatedAt());
        task.setPrecheckStudentId(student.getId());
        task.setPrecheckCanSubmit(result.isCanSubmit());
        if (!submittedFiles.isEmpty()) {
            task.setPrecheckSubmittedFilePathsJson(toJsonStringList(extractSortedPaths(submittedFiles)));
        }
        taskRepo.save(task);

        return result;
    }

    /**
     * Build a lightweight list of file DTOs for precheck AI keyword matching.
     * Accepts either validated submittedFiles or a simple fileUrl fallback.
     * Does NOT enforce path/contentType/size constraints (those are for actual submission).
     */
    private List<SubmittedFileDto> buildPrecheckFiles(SubmissionRequest req) {
        // Prefer validated list if provided
        if (req.getSubmittedFiles() != null && !req.getSubmittedFiles().isEmpty()) {
            List<SubmittedFileDto> safe = new ArrayList<>();
            for (SubmittedFileDto f : req.getSubmittedFiles()) {
                if (f != null && f.getFileName() != null) {
                    safe.add(f);
                }
            }
            if (!safe.isEmpty()) return safe;
        }
        // Fallback: build a synthetic DTO from fileUrl so AI can still read the filename
        if (req.getFileUrl() != null && !req.getFileUrl().isBlank()) {
            String url = req.getFileUrl();
            String fileName = url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url;
            return List.of(SubmittedFileDto.builder()
                    .fileName(fileName)
                    .path(url)
                    .url(url)
                    .contentType("application/octet-stream")
                    .size(1L)
                    .build());
        }
        return List.of();
    }


    @Transactional
    public SubmissionResponse submit(Long taskId, SubmissionRequest req) {
        User student = AuthUtil.getCurrentUser();
        if (student.getRole() != Role.STUDENT) {
            throw TaskHubException.forbidden("Only students can submit");
        }

        Task task = taskService.findTask(taskId);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw TaskHubException.badRequest("Task is not IN_PROGRESS");
        }
        if (!escrowService.isEscrowFunded(taskId)) {
            throw TaskHubException.badRequest("Escrow is not funded for this task");
        }
        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(student.getId())) {
            throw TaskHubException.forbidden("Not assigned to you");
        }

        List<SubmittedFileDto> submittedFiles = normalizeSubmittedFiles(req, taskId, student.getId());
        String legacyFileUrl = trimToNull(req.getFileUrl());
        if (submittedFiles.isEmpty() && legacyFileUrl == null) {
            throw TaskHubException.badRequest("Either submittedFiles or fileUrl is required");
        }
        validateLatestPrecheckForSubmit(task, student, submittedFiles);
        SubmissionAIResult latestPrecheck = fromAiResultJson(task.getSubmissionAIResultJson());
        int score = calculatePrecheckScore(latestPrecheck);

        boolean isRevision = !submissionRepo.findByTaskId(taskId).isEmpty();
        Submission submission = Submission.builder()
                .task(task)
                .student(student)
                .fileUrl(submittedFiles.isEmpty() ? legacyFileUrl : null)
                .submittedFilesJson(submittedFiles.isEmpty() ? null : toJson(submittedFiles))
                .notes(req.getNotes())
                .aiScore(score)
                .isRevision(isRevision)
                .aiReport(score < 70
                        ? "Warning: Precheck passed but score below 70%. Submission allowed but may need revision."
                        : "Submission meets criteria.")
                .build();
        submissionRepo.save(submission);

        taskService.transition(task, TaskStatus.SUBMITTED);
        taskRepo.save(task);

        return toResponse(submission);
    }

    @Transactional
    public RevisionRequestResponse requestRevision(Long taskId, RevisionRequest req) {
        if (req == null) {
            throw TaskHubException.badRequest("Request body is required");
        }
        if (isBlank(req.getReason())) {
            throw TaskHubException.badRequest("reason is required");
        }
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER) {
            throw TaskHubException.forbidden("Only hirers can request revision");
        }

        Task task = taskService.findTask(taskId);
        if (task.getHirer() == null || !task.getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("Not your task");
        }
        if (task.getStatus() != TaskStatus.SUBMITTED) {
            throw TaskHubException.badRequest("Task is not SUBMITTED");
        }
        if (task.getAssignedTo() == null) {
            throw TaskHubException.badRequest("Task has no assigned student");
        }

        Submission latestSubmission = submissionRepo.findTopByTaskIdOrderBySubmittedAtDesc(taskId)
                .orElseThrow(() -> TaskHubException.badRequest("No latest submission found"));
        SubmissionAIResult aiResult = fromAiResultJson(task.getSubmissionAIResultJson());
        if (aiResult == null) {
            throw TaskHubException.badRequest("Submission AI result is required before requesting revision");
        }

        int currentRevisionCount = currentRevisionCount(task, taskId);
        if (currentRevisionCount >= 3) {
            throw TaskHubException.badRequest("Maximum revision requests reached. Please dispute or resolve the task.");
        }

        List<RevisionSuggestionResponse> suggestions = buildRevisionSuggestions(aiResult);
        if (suggestions.isEmpty()) {
            throw TaskHubException.badRequest("All criteria are met. Revision is not recommended.");
        }

        com.taskhub.entity.RevisionRequest revision = com.taskhub.entity.RevisionRequest.builder()
                .task(task)
                .submission(latestSubmission)
                .requestedBy(hirer)
                .student(task.getAssignedTo())
                .revisionNumber(currentRevisionCount + 1)
                .reason(req.getReason().trim())
                .description(trimToNull(req.getDescription()))
                .aiSuggestionsJson(toRevisionSuggestionsJson(suggestions))
                .build();
        com.taskhub.entity.RevisionRequest savedRevision = revisionRequestRepo.save(revision);

        task.setRevisionCount(currentRevisionCount + 1);
        clearLatestPrecheck(task);
        taskService.transition(task, TaskStatus.IN_PROGRESS);
        taskRepo.save(task);
        notificationService.notifyRevisionRequested(
                task.getAssignedTo().getId(),
                task.getTitle(),
                task.getId(),
                savedRevision.getReason(),
                savedRevision.getDescription());

        return toRevisionResponse(savedRevision);
    }

    @Transactional
    public void approveSubmission(Long taskId) {
        User hirer = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);
        if (!task.getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("Not your task");
        }
        if (task.getStatus() != TaskStatus.SUBMITTED) {
            throw TaskHubException.badRequest("Task is not SUBMITTED");
        }

        // Lấy submission mới nhất
        List<Submission> submissions = submissionRepo.findByTaskId(taskId);
        if (!submissions.isEmpty()) {
            Submission latest = submissions.stream()
                    .max(Comparator.comparing(Submission::getSubmittedAt))
                    .orElse(submissions.get(0));

            // Nếu chưa đánh giá AI → chỉ cập nhật status
            // Nếu đã có evaluation → lấy finalScore/finalStars để hiển thị
            if (latest.getFinalScore() == null) {
                // Khuyến nghị Hirer chạy AI evaluation trước khi approve
                log.info("Approving submission {} without AI evaluation", latest.getId());
            } else {
                log.info("Approving submission {} with AI score: {}, stars: {}",
                        latest.getId(), latest.getFinalScore(), latest.getFinalStars());
            }

            // Cập nhật evaluation timestamp nếu chưa có
            if (latest.getEvaluatedAt() == null) {
                latest.setEvaluatedAt(java.time.LocalDateTime.now());
                latest.setEvaluatedByHirerId(hirer.getId());
                submissionRepo.save(latest);
            }
        }

        task.getAcceptanceCriteria().forEach(c -> c.setStatus(CriteriaStatus.PASSED));
        taskService.transition(task, TaskStatus.COMPLETED);
        taskRepo.save(task);
        escrowService.releaseEscrow(taskId);
    }
    private void checkViewPermission(Task task, User currentUser) {
        boolean isHirerOwner = task.getHirer() != null && task.getHirer().getId().equals(currentUser.getId());
        boolean isAssignedStudent = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(currentUser.getId());

        if (!isHirerOwner && !isAssignedStudent) {
            throw TaskHubException.forbidden("You do not have permission to view this task's submissions.");
        }
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getTaskSubmissions(Long taskId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);

        // THÊM DÒNG NÀY VÀO: Bắt buộc check quyền trước khi query database
        checkViewPermission(task, currentUser);

        return submissionRepo.findByTaskId(taskId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LatestSubmissionResultResponse getLatest(Long taskId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);
        checkViewPermission(task, currentUser);

        SubmissionResponse latestSubmission = submissionRepo.findTopByTaskIdOrderBySubmittedAtDesc(taskId)
                .map(this::toResponse)
                .orElse(null);
        int revisionCount = currentRevisionCount(task, taskId);
        List<RevisionRequestResponse> revisionHistory = revisionRequestRepo.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(this::toRevisionResponse)
                .toList();
        RevisionRequestResponse latestRevision = revisionHistory.isEmpty()
                ? null
                : revisionHistory.get(revisionHistory.size() - 1);

        return LatestSubmissionResultResponse.builder()
                .taskId(task.getId())
                .taskStatus(task.getStatus())
                .latestSubmission(latestSubmission)
                .submissionAIResult(fromAiResultJson(task.getSubmissionAIResultJson()))
                .revisionCount(revisionCount)
                .latestRevision(latestRevision)
                .revisionHistory(revisionHistory)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RevisionRequestResponse> getRevisionHistory(Long taskId) {
        User currentUser = AuthUtil.getCurrentUser();
        Task task = taskService.findTask(taskId);
        checkViewPermission(task, currentUser);

        return revisionRequestRepo.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(this::toRevisionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String generateDisputeReport(Long taskId) {
        Task task = taskService.findTask(taskId);
        List<Submission> subs = submissionRepo.findByTaskId(taskId);
        if (subs.isEmpty()) {
            throw TaskHubException.badRequest("No submissions found");
        }

        Submission latest = subs.get(subs.size() - 1);
        List<String> criteria = task.getAcceptanceCriteria().stream()
                .map(AcceptanceCriteria::getDescription)
                .toList();
        return aiValidation.generateDisputeReport(latest.getNotes(), criteria);
    }

    private SubmissionResponse toResponse(Submission s) {
        return SubmissionResponse.builder()
                .id(s.getId())
                .taskId(s.getTask().getId())
                .studentId(s.getStudent().getId())
                .studentName(s.getStudent().getFullName())
                .fileUrl(s.getFileUrl())
                .submittedFiles(fromJson(s.getSubmittedFilesJson()))
                .notes(s.getNotes())
                .aiScore(s.getAiScore())
                .aiReport(s.getAiReport())
                .finalScore(s.getFinalScore())
                .finalStars(s.getFinalStars())
                .finalRating(s.getFinalRating())
                .finalAssessment(s.getFinalAssessment())
                .hirerOverridden(s.getHirerOverridden())
                .evaluatedAt(s.getEvaluatedAt())
                .isRevision(s.getIsRevision())
                .submittedAt(s.getSubmittedAt())
                .build();
    }

    private RevisionRequestResponse toRevisionResponse(com.taskhub.entity.RevisionRequest revision) {
        return RevisionRequestResponse.builder()
                .id(revision.getId())
                .taskId(revision.getTask().getId())
                .submissionId(revision.getSubmission() != null ? revision.getSubmission().getId() : null)
                .requestedById(revision.getRequestedBy().getId())
                .studentId(revision.getStudent().getId())
                .revisionNumber(revision.getRevisionNumber())
                .reason(revision.getReason())
                .description(revision.getDescription())
                .aiSuggestions(fromRevisionSuggestionsJson(revision.getAiSuggestionsJson()))
                .createdAt(revision.getCreatedAt())
                .build();
    }

    private List<RevisionSuggestionResponse> buildRevisionSuggestions(SubmissionAIResult aiResult) {
        if (aiResult.getCriteriaResults() == null) {
            return List.of();
        }
        return aiResult.getCriteriaResults().stream()
                .filter(result -> result.getStatus() != null)
                .filter(result -> "PARTIAL".equalsIgnoreCase(result.getStatus())
                        || "FAILED".equalsIgnoreCase(result.getStatus()))
                .map(this::toRevisionSuggestion)
                .toList();
    }

    private RevisionSuggestionResponse toRevisionSuggestion(CriteriaAIResult result) {
        return RevisionSuggestionResponse.builder()
                .index(result.getIndex())
                .criteria(result.getCriteria())
                .status(result.getStatus())
                .suggestion(result.getSuggestion())
                .build();
    }

    private int currentRevisionCount(Task task, Long taskId) {
        int taskRevisionCount = task.getRevisionCount() == null ? 0 : task.getRevisionCount();
        long persistedRevisionCount = revisionRequestRepo.countByTaskId(taskId);
        return (int) Math.max(taskRevisionCount, persistedRevisionCount);
    }

    private void clearLatestPrecheck(Task task) {
        task.setSubmissionAIResultJson(null);
        task.setLatestPrecheckAt(null);
        task.setPrecheckStudentId(null);
        task.setPrecheckCanSubmit(null);
        task.setPrecheckSubmittedFilePathsJson(null);
    }

    private List<SubmittedFileDto> normalizeSubmittedFiles(SubmissionRequest req, Long taskId, Long currentUserId) {
        if (req.getSubmittedFiles() == null || req.getSubmittedFiles().isEmpty()) {
            return List.of();
        }

        List<SubmittedFileDto> files = new ArrayList<>();
        for (SubmittedFileDto file : req.getSubmittedFiles()) {
            validateSubmittedFile(file, taskId, currentUserId);
            files.add(file);
        }
        return files;
    }

    private void validateSubmittedFile(SubmittedFileDto file, Long taskId, Long currentUserId) {
        if (file == null) {
            throw TaskHubException.badRequest("submittedFiles contains an empty file metadata item");
        }
        if (isBlank(file.getFileName())) {
            throw TaskHubException.badRequest("submittedFiles.fileName is required");
        }
        if (isBlank(file.getPath())) {
            throw TaskHubException.badRequest("submittedFiles.path is required");
        }
        if (file.getPath().contains("../") || file.getPath().contains("..\\")) {
            throw TaskHubException.badRequest("submittedFiles.path must not contain path traversal");
        }

        String taskPrefix = "submissions/task-" + taskId + "/";
        if (!file.getPath().startsWith(taskPrefix)) {
            throw TaskHubException.badRequest("submittedFiles.path must start with " + taskPrefix);
        }
        if (!file.getPath().contains("/user-" + currentUserId + "/")) {
            throw TaskHubException.badRequest("submittedFiles.path must belong to the current user");
        }

        String contentType = file.getContentType();
        if (contentType == null || !FileUploadValidator.ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw TaskHubException.badRequest("Unsupported submittedFiles.contentType: " + contentType);
        }
        if (file.getSize() == null || file.getSize() <= 0 || file.getSize() > FileUploadValidator.MAX_FILE_SIZE_BYTES) {
            throw TaskHubException.badRequest("submittedFiles.size must be greater than 0 and not exceed 200MB");
        }
    }

    private String buildSubmissionSummary(String notes, List<SubmittedFileDto> submittedFiles, String legacyFileUrl) {
        StringBuilder summary = new StringBuilder();
        if (notes != null) {
            summary.append(notes).append(' ');
        }
        if (legacyFileUrl != null) {
            summary.append(legacyFileUrl).append(' ');
        }
        for (SubmittedFileDto file : submittedFiles) {
            summary.append(file.getFileName()).append(' ')
                    .append(file.getPath()).append(' ')
                    .append(file.getContentType()).append(' ');
        }
        return summary.toString();
    }

    private String toJson(List<SubmittedFileDto> submittedFiles) {
        try {
            return objectMapper.writeValueAsString(submittedFiles);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot serialize submitted files");
        }
    }

    private String toJsonStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot serialize precheck file paths");
        }
    }

    private String toJson(SubmissionAIResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot serialize submission AI result");
        }
    }

    private String toRevisionSuggestionsJson(List<RevisionSuggestionResponse> suggestions) {
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot serialize revision suggestions");
        }
    }

    private SubmissionAIResult fromAiResultJson(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SubmissionAIResult.class);
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot parse submission AI result");
        }
    }

    private List<String> fromStringListJson(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot parse precheck file paths");
        }
    }

    private List<RevisionSuggestionResponse> fromRevisionSuggestionsJson(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RevisionSuggestionResponse>>() {});
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot parse revision suggestions");
        }
    }

    private List<SubmittedFileDto> fromJson(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SubmittedFileDto>>() {});
        } catch (JsonProcessingException ex) {
            throw TaskHubException.internalError("Cannot parse submitted files");
        }
    }

    private void validateLatestPrecheckForSubmit(Task task, User student, List<SubmittedFileDto> submittedFiles) {
        SubmissionAIResult latestPrecheck = fromAiResultJson(task.getSubmissionAIResultJson());
        if (latestPrecheck == null || task.getPrecheckStudentId() == null) {
            throw TaskHubException.badRequest("Precheck is required before submission");
        }
        if (!task.getPrecheckStudentId().equals(student.getId())) {
            throw TaskHubException.badRequest("Precheck is required before submission");
        }
        boolean canSubmit = Boolean.TRUE.equals(task.getPrecheckCanSubmit()) && latestPrecheck.isCanSubmit();
        if (!canSubmit) {
            throw TaskHubException.badRequest("Latest precheck does not allow submission");
        }

        // Only compare file paths when submittedFiles were explicitly provided.
        // When using legacy fileUrl mode (mobile app), skip path comparison.
        if (!submittedFiles.isEmpty()) {
            List<String> precheckPaths = fromStringListJson(task.getPrecheckSubmittedFilePathsJson());
            List<String> submitPaths = extractSortedPaths(submittedFiles);
            if (!precheckPaths.equals(submitPaths)) {
                throw TaskHubException.badRequest("Submitted files changed after precheck. Please run precheck again.");
            }
        }
    }

    private int calculatePrecheckScore(SubmissionAIResult latestPrecheck) {
        if (latestPrecheck == null || latestPrecheck.getCriteriaResults() == null || latestPrecheck.getCriteriaResults().isEmpty()) {
            return 0;
        }
        long metCount = latestPrecheck.getCriteriaResults().stream()
                .filter(result -> "MET".equalsIgnoreCase(result.getStatus()))
                .count();
        return (int) ((metCount * 100.0) / latestPrecheck.getCriteriaResults().size());
    }

    private List<String> extractSortedPaths(List<SubmittedFileDto> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(SubmittedFileDto::getPath)
                .sorted(Comparator.nullsLast(String::compareTo))
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

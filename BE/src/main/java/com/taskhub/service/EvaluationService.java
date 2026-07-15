package com.taskhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.EvaluationOverrideRequest;
import com.taskhub.dto.request.EvaluationRequest;
import com.taskhub.dto.response.EvaluationResponse;
import com.taskhub.entity.*;
import com.taskhub.enums.EvaluationStatus;
import com.taskhub.enums.Role;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.*;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service xử lý evaluation flow:
 * 1. AI phân tích & đối chiếu submission với criteria → tạo EvaluationRecords
 * 2. Tính điểm theo trọng số → quy đổi sang sao
 * 3. Hirer xác nhận hoặc override kết quả AI
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    private final EvaluationRecordRepository evaluationRecordRepository;
    private final SubmissionRepository submissionRepository;
    private final AcceptanceCriteriaRepository criteriaRepository;
    private final TaskHubAiService aiService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────────
    // STAR RATING MAPPING
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Map % đạt → sao (1-5) theo thang điểm cố định.
     */
    public static int percentageToStars(double percentage) {
        if (percentage >= 95) return 5;
        if (percentage >= 85) return 4;
        if (percentage >= 70) return 3;
        if (percentage >= 50) return 2;
        return 1;
    }

    /**
     * Map sao → mô tả text.
     */
    public static String starsToLabel(int stars) {
        return switch (stars) {
            case 5 -> "Xuất sắc";
            case 4 -> "Tốt";
            case 3 -> "Khá";
            case 2 -> "Trung bình";
            default -> "Chưa đạt";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // WEIGHTED SCORE CALCULATION
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Tính điểm tổng theo trọng số từ danh sách EvaluationRecords.
     */
    public static double calculateWeightedScore(List<EvaluationRecord> records) {
        if (records == null || records.isEmpty()) return 0;

        double totalWeight = 0;
        double weightedSum = 0;

        for (EvaluationRecord r : records) {
            double weight = r.getWeight() != null ? r.getWeight() : 1.0;
            double percentage = r.getPercentage();
            weightedSum += percentage * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0) return 0;
        return weightedSum / totalWeight;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // AI EVALUATION
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public EvaluationResponse evaluateSubmission(EvaluationRequest request) {
        // 1. Load submission
        Submission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> TaskHubException.notFound("Submission not found"));

        Task task = submission.getTask();
        List<AcceptanceCriteria> criteriaList;

        // 2. Lấy criteria cần đánh giá
        if (request.getCriteriaIds() != null && !request.getCriteriaIds().isEmpty()) {
            criteriaList = criteriaRepository.findAllById(request.getCriteriaIds());
            boolean completeSelection = criteriaList.size() == request.getCriteriaIds().size();
            boolean belongsToSubmissionTask = criteriaList.stream()
                    .allMatch(criteria -> criteria.getTask() != null
                            && criteria.getTask().getId().equals(task.getId()));
            if (!completeSelection || !belongsToSubmissionTask) {
                throw TaskHubException.badRequest("All evaluation criteria must belong to the submission task");
            }
        } else {
            criteriaList = criteriaRepository.findByTaskIdOrderByIdAsc(task.getId());
        }

        if (criteriaList.isEmpty()) {
            throw TaskHubException.badRequest("No criteria found for evaluation");
        }

        // 3. Gọi AI phân tích submission
        com.taskhub.dto.request.AiEvaluationRequest aiRequest =
                com.taskhub.dto.request.AiEvaluationRequest.builder()
                        .submissionId(submission.getId())
                        .taskId(task.getId())
                        .attachmentUrl(request.getFileUrl() != null ? request.getFileUrl() : submission.getFileUrl())
                        .customCriteria(buildCriteriaJson(criteriaList))
                        .build();

        com.taskhub.dto.response.AiEvaluationResponse aiResult = aiService.evaluateSubmission(aiRequest);

        // 4. Tạo EvaluationRecords cho từng tiêu chí
        List<EvaluationRecord> records = new ArrayList<>();

        // Normalize weights
        List<Double> weights = normalizeWeights(request.getCustomWeights(), criteriaList.size());

        for (int i = 0; i < criteriaList.size(); i++) {
            AcceptanceCriteria criteria = criteriaList.get(i);
            double weight = weights.get(i);
            double maxScore = 10.0; // default

            // Parse AI result cho criteria này
            Double aiScore = null;
            String aiFeedback = null;

            if (aiResult.getCriteriaScores() != null && i < aiResult.getCriteriaScores().size()) {
                var cs = aiResult.getCriteriaScores().get(i);
                aiScore = cs.getScore();
                maxScore = cs.getMaxScore() != null ? cs.getMaxScore() : 10.0;
                aiFeedback = cs.getFeedback();
            }

            // Default nếu AI không trả đủ
            if (aiScore == null) {
                aiScore = 5.0;
                aiFeedback = "AI không phân tích được chi tiết cho tiêu chí này.";
            }

            double percentage = (aiScore / maxScore) * 100;
            int stars = percentageToStars(percentage);

            EvaluationRecord record = EvaluationRecord.builder()
                    .submission(submission)
                    .criteria(criteria)
                    .aiScore(aiScore)
                    .maxScore(maxScore)
                    .weight(weight)
                    .status(EvaluationStatus.AI_ANALYZED)
                    .aiFeedback(aiFeedback)
                    .stars(stars)
                    .evaluatedBy("AI")
                    .build();

            records.add(evaluationRecordRepository.save(record));
        }

        // 5. Tính điểm tổng theo trọng số
        double totalScore = calculateWeightedScore(records);
        int totalStars = percentageToStars(totalScore);
        String ratingLabel = starsToLabel(totalStars);

        // 6. Cập nhật submission
        submission.setFinalScore(totalScore);
        submission.setFinalStars(totalStars);
        submission.setFinalRating(ratingLabel);
        submission.setFinalAssessment(aiResult.getOverallAssessment());
        submission.setHirerOverridden(false);
        submission.setEvaluationRecords(records);
        submissionRepository.save(submission);

        // 7. Build response
        return buildEvaluationResponse(submission, records, aiResult);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HIRER OVERRIDE / CONFIRM
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public EvaluationResponse overrideEvaluation(EvaluationOverrideRequest request) {
        User hirer = AuthUtil.getCurrentUser();
        if (hirer.getRole() != Role.HIRER) {
            throw TaskHubException.forbidden("Only hirers can override evaluations");
        }

        Submission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> TaskHubException.notFound("Submission not found"));

        // Verify hirer owns this task
        if (submission.getTask().getHirer() == null ||
            !submission.getTask().getHirer().getId().equals(hirer.getId())) {
            throw TaskHubException.forbidden("You don't own this task");
        }

        List<EvaluationRecord> records = evaluationRecordRepository
                .findBySubmissionIdOrderByIdAsc(submission.getId());

        if (records.isEmpty()) {
            throw TaskHubException.badRequest("No evaluation records found. Run AI evaluation first.");
        }

        boolean isFullOverride = Boolean.TRUE.equals(request.getFullOverride());

        if (isFullOverride) {
            // Hirer ghi đè toàn bộ kết quả AI
            applyFullOverride(submission, records, request, hirer);
        } else {
            // Hirer chỉnh sửa từng tiêu chí
            applyPartialOverride(records, request, hirer);
        }

        // Recalculate total score
        double totalScore = calculateWeightedScore(records);
        int totalStars = percentageToStars(totalScore);
        String ratingLabel = starsToLabel(totalStars);

        // Update submission
        submission.setFinalScore(totalScore);
        submission.setFinalStars(totalStars);
        submission.setFinalRating(ratingLabel);
        submission.setHirerOverridden(true);
        submission.setEvaluatedByHirerId(hirer.getId());
        submission.setEvaluatedAt(LocalDateTime.now());

        if (request.getOverallAssessment() != null && !request.getOverallAssessment().isBlank()) {
            submission.setFinalAssessment(request.getOverallAssessment());
        }

        submissionRepository.save(submission);

        return buildEvaluationResponse(submission, records, null);
    }

    private void applyFullOverride(Submission submission, List<EvaluationRecord> records,
                                   EvaluationOverrideRequest request, User hirer) {
        double newScore = request.getOverrideScore() != null ? request.getOverrideScore() : 0;
        int newStars = request.getOverrideStars() != null ? request.getOverrideStars() : percentageToStars(newScore);
        String newRating = request.getOverrideRating() != null ? request.getOverrideRating() : starsToLabel(newStars);

        // Update all records
        for (EvaluationRecord record : records) {
            record.setStatus(EvaluationStatus.HIRER_OVERRIDDEN);
            record.setEvaluatedBy("HIRER");
            record.setHirerFeedback(request.getOverrideAssessment());
            record.setAiScore(newScore);
            record.setStars(newStars);
            evaluationRecordRepository.save(record);
        }

        submission.setFinalScore(newScore);
        submission.setFinalStars(newStars);
        submission.setFinalRating(newRating);
    }

    private void applyPartialOverride(List<EvaluationRecord> records,
                                     EvaluationOverrideRequest request, User hirer) {
        List<Double> newScores = request.getScores();
        List<String> newFeedbacks = request.getFeedbacks();
        List<Double> newWeights = request.getWeights();

        for (int i = 0; i < records.size(); i++) {
            EvaluationRecord record = records.get(i);

            // Update score if provided
            if (newScores != null && i < newScores.size() && newScores.get(i) != null) {
                double newScore = newScores.get(i);
                record.setAiScore(newScore);
                record.setStars(percentageToStars(record.getPercentage()));
                record.setStatus(EvaluationStatus.HIRER_MODIFIED);
            }

            // Update feedback if provided
            if (newFeedbacks != null && i < newFeedbacks.size() && newFeedbacks.get(i) != null) {
                record.setHirerFeedback(newFeedbacks.get(i));
            }

            // Update weight if provided
            if (newWeights != null && i < newWeights.size() && newWeights.get(i) != null) {
                record.setWeight(newWeights.get(i));
            }

            record.setEvaluatedBy("HIRER");
            evaluationRecordRepository.save(record);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GET EXISTING EVALUATION
    // ─────────────────────────────────────────────────────────────────────────────

    public EvaluationResponse getEvaluation(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> TaskHubException.notFound("Submission not found"));

        User currentUser = AuthUtil.getCurrentUser();
        Task task = submission.getTask();
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isOwner = task.getHirer() != null
                && task.getHirer().getId().equals(currentUser.getId());
        boolean isAssignedStudent = task.getAssignedTo() != null
                && task.getAssignedTo().getId().equals(currentUser.getId());
        if (!isAdmin && !isOwner && !isAssignedStudent) {
            throw TaskHubException.forbidden("You do not have permission to view this evaluation");
        }

        List<EvaluationRecord> records = evaluationRecordRepository
                .findBySubmissionIdOrderByIdAsc(submissionId);

        if (records.isEmpty()) {
            return null; // Chưa có evaluation
        }

        return buildEvaluationResponse(submission, records, null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private String buildCriteriaJson(List<AcceptanceCriteria> criteria) {
        List<Map<String, String>> items = criteria.stream()
                .map(c -> Map.of(
                        "criterion", c.getDescription(),
                        "maxScore", "10"
                ))
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Double> normalizeWeights(List<Double> customWeights, int size) {
        if (customWeights == null || customWeights.isEmpty()) {
            // Default: trọng số bằng nhau
            double equal = 1.0 / size;
            return java.util.Collections.nCopies(size, equal);
        }

        // Normalize tổng về 1.0
        double total = customWeights.stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 1.0) < 0.001) {
            return customWeights;
        }
        return customWeights.stream()
                .map(w -> w / total)
                .collect(Collectors.toList());
    }

    private EvaluationResponse buildEvaluationResponse(Submission submission,
                                                      List<EvaluationRecord> records,
                                                      com.taskhub.dto.response.AiEvaluationResponse aiResult) {
        List<EvaluationResponse.CriteriaEvaluationResult> criteriaResults = new ArrayList<>();

        for (EvaluationRecord r : records) {
            criteriaResults.add(EvaluationResponse.CriteriaEvaluationResult.builder()
                    .criteriaId(r.getCriteria().getId())
                    .criteriaDescription(r.getCriteria().getDescription())
                    .weight(r.getWeight())
                    .aiScore(r.getAiScore())
                    .maxScore(r.getMaxScore())
                    .percentage(r.getPercentage())
                    .stars(r.getStars())
                    .status(r.getStatus().name())
                    .aiFeedback(r.getAiFeedback())
                    .build());
        }

        int criteriaMetCount = (int) records.stream()
                .filter(r -> r.getPercentage() >= 70)
                .count();

        return EvaluationResponse.builder()
                .submissionId(submission.getId())
                .taskId(submission.getTask().getId())
                .criteriaResults(criteriaResults)
                .totalScore(submission.getFinalScore())
                .stars(submission.getFinalStars())
                .ratingLabel(submission.getFinalRating())
                .overallAssessment(submission.getFinalAssessment())
                .strengths(aiResult != null ? aiResult.getStrengths() : null)
                .weaknesses(aiResult != null ? aiResult.getWeaknesses() : null)
                .suggestions(aiResult != null ? aiResult.getSuggestions() : null)
                .criteriaMetCount(criteriaMetCount)
                .criteriaTotalCount(records.size())
                .evaluationStatus(records.isEmpty() ? null : records.get(0).getStatus().name())
                .isHirerOverridden(submission.getHirerOverridden())
                .evaluatedAt(submission.getEvaluatedAt())
                .build();
    }
}

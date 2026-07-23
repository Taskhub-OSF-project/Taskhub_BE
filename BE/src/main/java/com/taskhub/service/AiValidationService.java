package com.taskhub.service;

import com.taskhub.dto.SubmittedFileDto;
import com.taskhub.dto.response.CriteriaAIResult;
import com.taskhub.dto.response.SubmissionAIResult;
import com.taskhub.entity.CriteriaValidationDetail;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service kiểm tra chất lượng criteria bằng heuristic (không phải LLM thật).
 * Thuộc module AI, được gọi từ TaskService/SubmissionService.
 */
@Service
public class AiValidationService {

    private static final int MIN_LENGTH = 20;
    private static final int MIN_WORDS = 5;

    private static final List<String> VAGUE_TERMS = List.of(
            "dep", "tot", "on", "hay", "xin", "cute", "dinh", "vuot troi", "chat luong", "chat luong cao",
            "chuyen nghiep", "an tuong", "bat mat", "sang trong", "hieu qua", "hop ly", "phu hop",
            "tot nhat", "dep nhat", "on dinh", "mem min", "doc dao", "an tuong manh",
            "quality", "good", "nice", "beautiful", "decent", "fine", "okay", "great", "excellent",
            "perfect", "professional", "awesome", "amazing", "stunning", "pretty", "cool"
    );

    private static final Pattern VAGUE_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", VAGUE_TERMS) + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern LAZY_CRITERIA = Pattern.compile(
            "^(lam|lam cho|lam sao|giup|giup toi|ok|oke|duoc|duoc roi|xong|het|test|abc|123|tuy y|tuy chon|"
                    + "khong can|khong quan trong|binh thuong|tam duoc|kieu gi cung duoc).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Set<String> PRECHECK_STOPWORDS = Set.of(
            "va", "hoac", "la", "co", "cac", "cua", "cho", "theo", "voi", "trong", "ngoai", "duoc",
            "can", "phai", "nay", "kia", "mot", "hai", "ba", "bon", "nam", "sau", "bay", "tam", "chin",
            "muoi", "toi", "thieu", "da", "it", "nhat", "nho", "hon", "lon", "day", "du", "yeu", "cau",
            "nop", "lam", "em", "anh", "chi", "file", "tep", "tap", "tin",
            "and", "or", "the", "a", "an", "to", "of", "for", "in", "on", "with", "by", "from", "as",
            "is", "are", "be", "must", "should", "required", "requirement", "submit", "submission"
    );

    /**
     * Validate danh sách criteria và trả chi tiết từng mục.
     * Output: ValidationResult (valid + details).
     */
    public ValidationResult validateCriteriaEnhanced(List<String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return new ValidationResult(false, "At least one acceptance criterion is required", List.of());
        }

        List<CriteriaValidationDetail> details = new ArrayList<>();
        boolean allValid = true;

        for (int i = 0; i < criteria.size(); i++) {
            var detail = validateSingleCriteria(i, criteria.get(i));
            details.add(detail);
            if (!detail.isValid()) allValid = false;
        }

        if (allValid) {
            return new ValidationResult(true, "All criteria are measurable and unambiguous", details);
        }
        long failedCount = details.stream().filter(d -> !d.isValid()).count();
        return new ValidationResult(false,
                String.format("%d criterion(s) are too vague or not measurable. Clarify before continuing.", failedCount),
                details);
    }

    private CriteriaValidationDetail validateSingleCriteria(int index, String raw) {
        String criteria = raw != null ? raw.trim() : "";

        if (criteria.isBlank()) {
            return invalid(index, criteria, "EMPTY",
                    "Criterion cannot be empty",
                    "Example: Deliver 1 file PDF, minimum 5 pages, font Arial 12pt");
        }

        if (criteria.length() < MIN_LENGTH) {
            return invalid(index, criteria, "TOO_SHORT",
                    "Too short — describe deliverable, quantity, and format",
                    generateLengthSuggestion(criteria));
        }

        if (wordCount(criteria) < MIN_WORDS) {
            return invalid(index, criteria, "TOO_FEW_WORDS",
                    "Not specific enough — add quantity, format, and how to verify",
                    generateMetricSuggestion(criteria));
        }

        if (LAZY_CRITERIA.matcher(normalize(criteria)).matches()) {
            return invalid(index, criteria, "LAZY",
                    "Looks like placeholder text — write a measurable requirement",
                    generateMetricSuggestion(criteria));
        }

        if (isMostlySubjective(criteria)) {
            return invalid(index, criteria, "SUBJECTIVE",
                    "Uses vague words (e.g. beautiful, good, quality) without measurable definition",
                    generateSpecificSuggestion(criteria));
        }

        if (VAGUE_PATTERN.matcher(normalize(criteria)).find() && !hasDeliverableOrMetric(criteria)) {
            return invalid(index, criteria, "VAGUE_TERM",
                    "Subjective wording without numbers or deliverable — define what 'good' means",
                    generateSpecificSuggestion(criteria));
        }

        if (!hasDeliverableOrMetric(criteria)) {
            return invalid(index, criteria, "NOT_MEASURABLE",
                    "Missing measurable element (file type, count, size, %, deadline, or dimension)",
                    generateMetricSuggestion(criteria));
        }

        return new CriteriaValidationDetail(index, criteria, true, null, null);
    }

    private boolean isMostlySubjective(String criteria) {
        String n = normalize(criteria);
        boolean hasVague = VAGUE_PATTERN.matcher(n).find();
        return hasVague && !hasDeliverableOrMetric(criteria);
    }

    private boolean hasDeliverableOrMetric(String criteria) {
        String lower = normalize(criteria);
        if (lower.matches(".*\\b\\d+\\b.*")) return true;
        return lower.contains("file") || lower.contains("tep") || lower.contains("tap tin")
                || lower.contains("dinh dang") || lower.contains("format") || lower.contains(".pdf")
                || lower.contains(".png") || lower.contains(".docx") || lower.contains(".xlsx")
                || lower.contains("kich thuoc") || lower.contains("size") || lower.contains("px")
                || lower.contains("mb") || lower.contains("kb") || lower.contains("trang")
                || lower.contains("dong") || lower.contains("tu ") || lower.contains("ky tu")
                || lower.contains("thoi gian") || lower.contains("phut") || lower.contains("gio")
                || lower.contains("ngay") || lower.contains("phan tram") || lower.contains("%")
                || lower.contains("it nhat") || lower.contains("toi thieu") || lower.contains("toi da")
                || lower.contains("chieu cao") || lower.contains("chieu rong") || lower.contains("1920")
                || lower.contains("slide") || lower.contains("ban giao") || lower.contains("deliverable");
    }

    private CriteriaValidationDetail invalid(int index, String criteria, String code, String issue, String suggestion) {
        return new CriteriaValidationDetail(index, criteria, false, code + ": " + issue, suggestion);
    }

    private int wordCount(String text) {
        if (text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private String normalize(String input) {
        // Chuẩn hóa để so khớp từ khóa không dấu.
        String n = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return n.replace('đ', 'd').replace('Đ', 'd');
    }

    private String generateLengthSuggestion(String criteria) {
        String base = criteria.isBlank() ? "Deliverable" : criteria;
        return base + " — e.g. 1 PNG file, 1920×1080 px, sRGB, max 5 MB";
    }

    private String generateSpecificSuggestion(String criteria) {
        return criteria.replaceAll("(?i)\\b(dep|tot|chat luong|chuyen nghiep|hay|on)\\b", "dat tieu chuan")
                + " — chi ro: so luong, dinh dang file, kich thuoc hoac % dat yeu cau";
    }

    private String generateMetricSuggestion(String criteria) {
        return criteria + " — e.g. 3 file PDF, moi file toi thieu 5 trang, font Arial 12pt";
    }

    /**
     * Chấm điểm submission dựa trên mức độ khớp keyword với criteria.
     */
    public int scoreSubmission(String submissionNotes, List<String> criteria) {
        if (criteria.isEmpty()) return 0;
        int matched = 0;
        String lower = submissionNotes != null ? submissionNotes.toLowerCase() : "";
        for (String c : criteria) {
            String[] words = c.toLowerCase().split("\\s+");
            long hits = java.util.Arrays.stream(words).filter(w -> w.length() > 3 && lower.contains(w)).count();
            if (hits >= words.length * 0.3) matched++;
        }
        return (int) ((matched * 100.0) / criteria.size());
    }

    /**
     * Sinh báo cáo tranh chấp dạng text để tham khảo nhanh.
     */
    public SubmissionAIResult evaluateSubmissionPrecheck(String notes, List<SubmittedFileDto> files, List<String> criteria) {
        List<String> safeCriteria = criteria != null ? criteria : List.of();
        Set<String> evidenceTokens = new LinkedHashSet<>(Arrays.asList(
                normalizeForKeyword(buildPrecheckText(notes, files)).split("\\s+")
        ));
        evidenceTokens.remove("");

        List<CriteriaAIResult> results = new ArrayList<>();
        int metCount = 0;
        int failedCount = 0;

        for (int i = 0; i < safeCriteria.size(); i++) {
            String criterion = safeCriteria.get(i) != null ? safeCriteria.get(i) : "";
            List<String> keywords = extractPrecheckKeywords(criterion);
            List<String> matchedKeywords = keywords.stream()
                    .filter(evidenceTokens::contains)
                    .toList();
            double ratio = keywords.isEmpty() ? 0 : (double) matchedKeywords.size() / keywords.size();

            String status;
            String suggestion = null;
            if (ratio >= 0.6) {
                status = "MET";
                metCount++;
            } else if (ratio > 0) {
                status = "PARTIAL";
                suggestion = "Bo sung bang chung hoac notes lien quan den: "
                        + String.join(", ", missingKeywords(keywords, matchedKeywords));
            } else {
                status = "FAILED";
                failedCount++;
                suggestion = "Chua thay bang chung dap ung criterion nay trong notes hoac metadata file.";
            }

            results.add(CriteriaAIResult.builder()
                    .index(i)
                    .criteria(criterion)
                    .status(status)
                    .locked("MET".equals(status))
                    .evidence(matchedKeywords.isEmpty()
                            ? "Matched keywords: none"
                            : "Matched keywords: " + String.join(", ", matchedKeywords))
                    .suggestion(suggestion)
                    .build());
        }

        int total = safeCriteria.size();
        boolean failedMoreThanHalf = total == 0 || failedCount > total / 2.0;
        boolean canSubmit = metCount > 0 && !failedMoreThanHalf;
        String overallStatus;
        if (total > 0 && metCount == total) {
            overallStatus = "PASSED";
        } else if (metCount == 0 || failedMoreThanHalf) {
            overallStatus = "FAILED";
        } else {
            overallStatus = "PARTIAL";
        }

        return SubmissionAIResult.builder()
                .overallStatus(overallStatus)
                .criteriaResults(results)
                .canSubmit(canSubmit)
                .evaluatedAt(java.time.LocalDateTime.now())
                .summary(metCount + "/" + total + " tiêu chí đạt yêu cầu.")
                .build();
    }

    private String buildPrecheckText(String notes, List<SubmittedFileDto> files) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, notes);
        if (files != null) {
            for (SubmittedFileDto file : files) {
                if (file == null) {
                    continue;
                }
                appendIfPresent(text, file.getFileName());
                appendIfPresent(text, file.getPath());
                appendIfPresent(text, file.getContentType());
            }
        }
        return text.toString();
    }

    private void appendIfPresent(StringBuilder text, String value) {
        if (value != null) {
            text.append(value).append(' ');
        }
    }

    private List<String> extractPrecheckKeywords(String criterion) {
        return Arrays.stream(normalizeForKeyword(criterion).split("\\s+"))
                .filter(token -> token.length() > 2)
                .filter(token -> !PRECHECK_STOPWORDS.contains(token))
                .distinct()
                .toList();
    }

    private List<String> missingKeywords(List<String> keywords, List<String> matchedKeywords) {
        Set<String> matchedSet = new LinkedHashSet<>(matchedKeywords);
        return keywords.stream()
                .filter(keyword -> !matchedSet.contains(keyword))
                .limit(8)
                .toList();
    }

    private String normalizeForKeyword(String input) {
        String normalized = normalize(input != null ? input : "");
        return normalized.replaceAll("[^a-z0-9]+", " ").trim();
    }

    public String generateDisputeReport(String submissionNotes, List<String> criteria) {
        StringBuilder sb = new StringBuilder("=== DISPUTE REPORT ===\n\n");
        String lower = submissionNotes != null ? submissionNotes.toLowerCase() : "";
        for (int i = 0; i < criteria.size(); i++) {
            String c = criteria.get(i);
            String[] words = c.toLowerCase().split("\\s+");
            long hits = java.util.Arrays.stream(words).filter(w -> w.length() > 3 && lower.contains(w)).count();
            boolean met = hits >= words.length * 0.3;
            sb.append(String.format("Criterion %d: %s\nStatus: %s\n\n", i + 1, c, met ? "MET" : "NOT MET"));
        }
        return sb.toString();
    }

    /**
     * Sinh báo cáo dispute structured (JSON-serializable) với recommendation 3 chiều.
     * - >= 70% MET → RELEASE_PAYMENT
     * - <= 30% MET → REQUEST_REVISION
     * - otherwise  → ESCALATE
     */
    public com.taskhub.dto.response.DisputeAIReport generateStructuredDisputeReport(
            Long taskId,
            String submissionNotes,
            List<String> criteria,
            String disputeReason,
            String disputeDescription) {

        List<String> safeCriteria = criteria != null ? criteria : List.of();
        String lower = normalizeForKeyword(submissionNotes != null ? submissionNotes : "");
        Set<String> evidenceTokens = new LinkedHashSet<>(Arrays.asList(lower.split("\\s+")));
        evidenceTokens.remove("");

        List<com.taskhub.dto.response.DisputeAIReport.CriterionAssessment> assessments = new ArrayList<>();
        int metCount = 0;

        for (int i = 0; i < safeCriteria.size(); i++) {
            String criterion = safeCriteria.get(i) != null ? safeCriteria.get(i) : "";
            List<String> keywords = extractPrecheckKeywords(criterion);
            List<String> matched = keywords.stream().filter(evidenceTokens::contains).toList();
            double ratio = keywords.isEmpty() ? 0 : (double) matched.size() / keywords.size();
            boolean met = ratio >= 0.3;
            if (met) metCount++;

            String evidence = matched.isEmpty()
                    ? "No matching keywords found"
                    : "Matched keywords: " + String.join(", ", matched);

            assessments.add(com.taskhub.dto.response.DisputeAIReport.CriterionAssessment.builder()
                    .criterionIndex(i)
                    .criterion(criterion)
                    .assessment(met ? "MET" : "NOT_MET")
                    .met(met)
                    .evidence(evidence)
                    .build());
        }

        int total = safeCriteria.size();
        int metPct = total == 0 ? 0 : (int) Math.round((metCount * 100.0) / total);

        String recommendation;
        if (metPct >= 70) {
            recommendation = "RELEASE_PAYMENT";
        } else if (metPct <= 30) {
            recommendation = "REQUEST_REVISION";
        } else {
            recommendation = "ESCALATE";
        }

        return com.taskhub.dto.response.DisputeAIReport.builder()
                .taskId(taskId)
                .assessments(assessments)
                .recommendation(recommendation)
                .metPercentage(metPct)
                .disputeReason(disputeReason)
                .disputeDescription(disputeDescription)
                .reportGeneratedAt(java.time.LocalDateTime.now())
                .build();
    }

    public record ValidationResult(boolean valid, String message, List<CriteriaValidationDetail> details) {
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public List<CriteriaValidationDetail> getDetails() { return details; }
    }

    public record CriteriaSuggestion(String issueReason, String suggestion) {}
}

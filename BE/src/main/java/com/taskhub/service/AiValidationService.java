package com.taskhub.service;

import com.taskhub.entity.CriteriaValidationDetail;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AiValidationService {

    private static final List<String> VAGUE_TERMS = List.of(
            "dep", "tot", "on", "chat luong", "chuyen nghiep", "quality", "good", "nice", "beautiful",
            "decent", "fine", "okay", "great", "excellent", "perfect", "professional"
    );

    private static final Pattern VAGUE_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", VAGUE_TERMS) + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final List<String> DELIVERABLE_KEYWORDS = List.of(
            "file", "tap tin", "so luong", "bao nhieu", "kich thuoc", "dinh dang", "format",
            "phan", "trang", "dong", "tu", "ky tu", "giay", "phut", "gio", "ngay", "tuan",
            "lan", "buoc", "giai doan", "mau", "font", "px", "mb", "gb", "minimum", "toi thieu",
            "maximum", "toi da", "it nhat", "nhieu nhat", "cu the", "chi tiet"
    );

    public ValidationResult validateCriteriaEnhanced(List<String> criteria) {
        List<CriteriaValidationDetail> details = new ArrayList<>();
        boolean allValid = true;

        for (int i = 0; i < criteria.size(); i++) {
            String c = criteria.get(i);
            var detail = validateSingleCriteria(i, c);
            details.add(detail);
            if (!detail.isValid()) allValid = false;
        }

        if (allValid) {
            return new ValidationResult(true, "All criteria pass validation", details);
        } else {
            long failedCount = details.stream().filter(d -> !d.isValid()).count();
            return new ValidationResult(false,
                    String.format("Found %d invalid criteria. Fix before locking.", failedCount),
                    details);
        }
    }

    private CriteriaValidationDetail validateSingleCriteria(int index, String criteria) {
        if (criteria.isBlank() || criteria.length() < 12) {
            return new CriteriaValidationDetail(index, criteria, false,
                    "Too short (< 12 characters)",
                    generateLengthSuggestion(criteria));
        }

        if (VAGUE_PATTERN.matcher(criteria).find()) {
            return new CriteriaValidationDetail(index, criteria, false,
                    "Contains subjective terms",
                    generateSpecificSuggestion(criteria));
        }

        if (!hasDeliverableOrMetric(criteria)) {
            return new CriteriaValidationDetail(index, criteria, false,
                    "Missing deliverable/metric",
                    generateMetricSuggestion(criteria));
        }

        return new CriteriaValidationDetail(index, criteria, true, null, null);
    }

    private boolean hasDeliverableOrMetric(String criteria) {
        String lower = criteria.toLowerCase();
        return lower.matches(".*\\b\\d+\\b.*") || // Has numbers
                lower.contains("file") || lower.contains("tep") ||
                lower.contains("dinh dang") || lower.contains("format") ||
                lower.contains("kich thuoc") || lower.contains("size") ||
                lower.contains("thoi gian") || lower.contains("deadline") ||
                lower.contains("phan tram") || lower.contains("%");
    }

    private String generateLengthSuggestion(String criteria) {
        return criteria + " with PNG format, minimum size 1920x1080px";
    }

    private String generateSpecificSuggestion(String criteria) {
        return criteria.replaceAll("(?i)\\b(dep|tot|chat luong|chuyen nghiep)\\b", "meets design standards");
    }

    private String generateMetricSuggestion(String criteria) {
        return criteria + " - deliver 3 PDF files, each minimum 5 pages";
    }

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

    // Use inner record that matches the response type needed
    public record ValidationResult(boolean valid, String message, List<CriteriaValidationDetail> details) {

        // Helper methods for compatibility
        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public List<CriteriaValidationDetail> getDetails() {
            return details;
        }
    }

    public record CriteriaSuggestion(String issueReason, String suggestion) {}
}
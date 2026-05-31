package com.taskhub.service;

import com.taskhub.entity.CriteriaValidationDetail;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    public record ValidationResult(boolean valid, String message, List<CriteriaValidationDetail> details) {
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public List<CriteriaValidationDetail> getDetails() { return details; }
    }

    public record CriteriaSuggestion(String issueReason, String suggestion) {}
}

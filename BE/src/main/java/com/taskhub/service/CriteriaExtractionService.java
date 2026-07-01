package com.taskhub.service;

import com.taskhub.dto.response.CriteriaExtractResponse;
import com.taskhub.dto.response.CriteriaExtractResponse.ExtractedCriterion;
import com.taskhub.exception.TaskHubException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service gợi ý criteria từ mô tả task (text) và / hoặc nội dung file brief.
 *
 * <p>Các phương pháp được dùng:
 * <ol>
 *   <li>Đọc trích đoạn text từ file PDF / TXT / DOCX (nếu Apache POI có sẵn).
 *       Với ảnh / nhị phân khác, lấy metadata (tên, kích thước, content-type).</li>
 *   <li>Kết hợp với {@code taskDescription} / {@code requirements} do client gửi kèm.</li>
 *   <li>Phân tích bằng Gemini để sinh criteria dựa trên brief thực.</li>
 *   <li>Nếu Gemini lỗi / thiếu key, fallback heuristic có dùng nội dung file.</li>
 * </ol>
 */
@Service
@Slf4j
public class CriteriaExtractionService {

    private static final long MAX_BYTES = 15 * 1024 * 1024;
    private static final int MAX_TEXT_PREVIEW = 4000;
    private final GeminiAiService geminiAiService;

    public CriteriaExtractionService(GeminiAiService geminiAiService) {
        this.geminiAiService = geminiAiService;
    }

    /**
     * Trả về danh sách criteria gợi ý dựa trên nội dung file + ngữ cảnh.
     */
    public CriteriaExtractResponse extractFromFile(MultipartFile file) {
        return extractFromFile(file, null, null);
    }

    /**
     * Trả về danh sách criteria gợi ý dựa trên nội dung file + mô tả task /
     * yêu cầu thêm (nếu có).
     */
    public CriteriaExtractResponse extractFromFile(
            MultipartFile file,
            String taskDescription,
            String extraRequirements
    ) {
        if (file == null || file.isEmpty())
            throw TaskHubException.badRequest("File is required");
        if (file.getSize() > MAX_BYTES)
            throw TaskHubException.badRequest("File too large (max 15 MB)");

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String type = detectType(fileName, file.getContentType());

        FileContent content = readContent(file, type);
        String combinedContext = buildContext(fileName, type, content, taskDescription, extraRequirements);

        CriteriaExtractResponse aiResult = tryGemini(fileName, type, combinedContext);
        if (aiResult != null) return aiResult;

        return CriteriaExtractResponse.builder()
                .fileName(fileName)
                .detectedType(type)
                .suggestions(buildHeuristicSuggestions(type, fileName, content.preview, taskDescription))
                .build();
    }

    // ── Content extraction ────────────────────────────────────────────────────

    private FileContent readContent(MultipartFile file, String type) {
        return switch (type) {
            case "TEXT" -> readTextFile(file);
            case "PDF" -> readPdfFile(file);
            case "DOCUMENT" -> readDocxFile(file);
            default -> new FileContent(file.getSize(), null, "binary-or-image: " + file.getSize() + " bytes");
        };
    }

    private FileContent readTextFile(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            return new FileContent(file.getSize(), preview(text), text.length() + " chars");
        } catch (Exception e) {
            return new FileContent(file.getSize(), null, "read-failed: " + e.getMessage());
        }
    }

    private FileContent readPdfFile(MultipartFile file) {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return new FileContent(file.getSize(), preview(text), doc.getNumberOfPages() + " pages, "
                    + text.length() + " chars");
        } catch (Exception e) {
            log.warn("PDF read failed ({}): {}", file.getOriginalFilename(), e.getMessage());
            return new FileContent(file.getSize(), null, "pdf-read-failed");
        }
    }

    private FileContent readDocxFile(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n'));
            for (org.apache.poi.xwpf.usermodel.XWPFTable tbl : doc.getTables()) {
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : tbl.getRows()) {
                    row.getTableCells().forEach(c -> sb.append(c.getText()).append('\t'));
                    sb.append('\n');
                }
            }
            String text = sb.toString();
            return new FileContent(file.getSize(), preview(text), "docx: " + text.length() + " chars");
        } catch (NoClassDefFoundError | Exception e) {
            log.warn("DOCX read failed ({}): {}", file.getOriginalFilename(), e.getMessage());
            return new FileContent(file.getSize(), null, "docx-read-unavailable");
        }
    }

    private String preview(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_TEXT_PREVIEW) return trimmed;
        return trimmed.substring(0, MAX_TEXT_PREVIEW) + "\n... [+" + (trimmed.length() - MAX_TEXT_PREVIEW) + " chars]";
    }

    private record FileContent(long size, String preview, String summary) {}

    private String buildContext(String fileName, String type, FileContent content,
                                String taskDescription, String extraRequirements) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(fileName).append(" (").append(type).append(", ")
                .append(content.summary).append(")\n\n");

        if (taskDescription != null && !taskDescription.isBlank()) {
            sb.append("## Mô tả công việc do người dùng cung cấp:\n")
                    .append(taskDescription.trim()).append("\n\n");
        }
        if (extraRequirements != null && !extraRequirements.isBlank()) {
            sb.append("## Yêu cầu bổ sung:\n").append(extraRequirements.trim()).append("\n\n");
        }
        if (content.preview != null && !content.preview.isBlank()) {
            sb.append("## Nội dung trích từ file (tối đa 4000 ký tự đầu):\n")
                    .append(content.preview).append("\n");
        }
        return sb.toString();
    }

    // ── Gemini attempt ────────────────────────────────────────────────────────

    private CriteriaExtractResponse tryGemini(String fileName, String type, String combinedContext) {
        try {
            com.taskhub.dto.response.AiCriteriaResponse resp = geminiAiService
                    .suggestCriteriaFromBrief(combinedContext, type, fileName);
            if (resp == null || resp.getSuggestions() == null || resp.getSuggestions().isEmpty()) {
                return null;
            }
            List<ExtractedCriterion> out = new ArrayList<>();
            for (com.taskhub.dto.response.AiCriteriaResponse.CriteriaSuggestion s : resp.getSuggestions()) {
                String rationale = s.getEvaluationGuide() != null ? s.getEvaluationGuide() : "AI suggestion";
                out.add(ExtractedCriterion.builder()
                        .text(buildCriterionText(s))
                        .rationale(rationale)
                        .build());
            }
            return CriteriaExtractResponse.builder()
                    .fileName(fileName)
                    .detectedType(type)
                    .suggestions(out)
                    .build();
        } catch (Exception e) {
            log.warn("Gemini criteria extraction failed, falling back to heuristic: {}", e.getMessage());
            return null;
        }
    }

    private String buildCriterionText(com.taskhub.dto.response.AiCriteriaResponse.CriteriaSuggestion s) {
        String name = s.getName() != null ? s.getName().trim() : "Tiêu chí";
        String desc = s.getDescription() != null ? s.getDescription().trim() : "";
        return desc.isEmpty() ? name : (name + ": " + desc);
    }

    // ── Heuristic fallback ─────────────────────────────────────────────────────

    private List<ExtractedCriterion> buildHeuristicSuggestions(String type, String fileName,
                                                              String preview, String taskDescription) {
        List<ExtractedCriterion> list = new ArrayList<>();
        String sourceHint = preview != null && !preview.isBlank()
                ? "trích từ nội dung file"
                : (taskDescription != null && !taskDescription.isBlank()
                    ? "dựa trên mô tả task"
                    : "theo loại file");

        switch (type) {
            case "PDF" -> {
                list.add(criterion(
                        "Giao 1 file PDF ten \"" + baseName(fileName) + "_final.pdf\", toi thieu 5 trang, font Arial 12pt, margin 2.5cm",
                        "PDF " + sourceHint + " — quy định rõ định dạng và độ dài"));
                list.add(criterion(
                        "Noi dung PDF khop 100% voi outline da dinh (muc 1–3), khong thieu muc bat buoc",
                        "Dam bao deliverable do luong duoc"));
            }
            case "SPREADSHEET" -> {
                list.add(criterion(
                        "Giao file Excel .xlsx: toi thieu 100 dong du lieu hop le, 0 dong trong, cot A–F day du",
                        "Phu hop nhap lieu / bao cao so lieu"));
                list.add(criterion(
                        "100% o so o cot D va E la dinh dang Number, khong chua text hoac #N/A",
                        "Tieu chi do luong cho chat luong du lieu"));
            }
            case "IMAGE" -> {
                list.add(criterion(
                        "Giao file PNG 1920x1080 px, sRGB, dung luong toi da 5 MB, khong watermark",
                        "Tieu chi thiet ke / banner co kich thuoc cu the"));
                list.add(criterion(
                        "Logo va text chinh doc duoc o kich thuoc 100% (khong mo, khong bi cat)",
                        "Tranh tu mo ho \"dep\" — xac minh bang mat"));
            }
            case "DOCUMENT" -> {
                list.add(criterion(
                        "Giao file DOCX, toi thieu 800 tu, font Times New Roman 13pt, line spacing 1.15",
                        "Van ban co metric ro rang"));
            }
            case "TEXT" -> {
                list.add(criterion(
                        "Giao file text/markdown dong le, ky tu ASCII + Unicode, khong co ky tu dieu khien",
                        "File text " + sourceHint));
                list.add(criterion(
                        "Noi dung khop 100% outline da mo ta trong brief, tu 500 den 5000 ky tu",
                        "Dam bao do dai va pham vi"));
            }
            default -> {
                list.add(criterion(
                        "Giao dung 1 file deliverable theo dinh dang da thong nhat trong brief, kem checklist tu kiem",
                        "File generic — can bo sung so luong va dinh dang"));
            }
        }
        return list;
    }

    private String detectType(String fileName, String contentType) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") || "application/pdf".equals(contentType)) return "PDF";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) return "SPREADSHEET";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))
            return "IMAGE";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "DOCUMENT";
        if (lower.endsWith(".txt") || lower.endsWith(".md") || (contentType != null && contentType.startsWith("text/")))
            return "TEXT";
        if (contentType != null && contentType.startsWith("image/")) return "IMAGE";
        return "GENERIC";
    }

    private ExtractedCriterion criterion(String text, String rationale) {
        return ExtractedCriterion.builder().text(text).rationale(rationale).build();
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

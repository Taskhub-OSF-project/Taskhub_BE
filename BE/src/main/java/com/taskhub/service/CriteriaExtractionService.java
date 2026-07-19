package com.taskhub.service;

import com.taskhub.dto.response.CriteriaExtractResponse;
import com.taskhub.dto.response.CriteriaExtractResponse.ExtractedCriterion;
import com.taskhub.exception.TaskHubException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_TEXT_PREVIEW = 8000;
    private static final int MIN_READABLE_CHARACTERS = 40;
    private static final int MAX_VISION_PAGES = 4;
    private static final int VISION_MAX_WIDTH = 1200;
    private static final int VISION_MAX_HEIGHT = 7000;
    private static final long MAX_EMBEDDED_IMAGE_PIXELS = 12_000_000;
    private static final long MAX_TOTAL_EMBEDDED_IMAGE_PIXELS = 24_000_000;
    private final TaskHubAiService aiService;

    public CriteriaExtractionService(TaskHubAiService aiService) {
        this.aiService = aiService;
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

        if ("IMAGE".equals(type)) {
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw TaskHubException.badRequest("Image too large (max 5 MB)");
            }
            try {
                byte[] bytes = file.getBytes();
                String imageFormat = detectImageFormat(bytes);
                return aiService.extractTaskBriefFromImage(
                        bytes, imageFormat, fileName, taskDescription, extraRequirements);
            } catch (TaskHubException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Cannot read uploaded image {}: {}", fileName, e.getMessage());
                throw TaskHubException.badRequest("Cannot read the uploaded image");
            }
        }

        FileContent content = readContent(file, type);
        if (!hasMeaningfulText(content.preview) && content.visionBytes != null) {
            return aiService.extractTaskBriefFromImage(
                    content.visionBytes,
                    content.visionFormat,
                    fileName,
                    combineContext(taskDescription, content.preview),
                    extraRequirements);
        }
        if (content.preview == null || content.preview.isBlank()) {
            throw unreadableBrief(type);
        }

        return aiService.extractTaskBriefFromText(
                content.preview, type, fileName, taskDescription, extraRequirements);
    }

    private TaskHubException unreadableBrief(String type) {
        return switch (type) {
            case "DOCUMENT" -> TaskHubException.badRequest(
                    "File DOCX không hợp lệ hoặc bị hỏng. Hãy mở bằng Word và lưu lại dưới dạng .docx rồi upload lại.");
            case "PDF" -> TaskHubException.badRequest(
                    "PDF không có nội dung chữ đọc được. Hãy upload PDF có text hoặc ảnh PNG/JPG rõ nét.");
            case "TEXT" -> TaskHubException.badRequest("File TXT không có nội dung đọc được.");
            default -> TaskHubException.badRequest(
                    "Định dạng brief chưa được hỗ trợ. Hãy dùng PNG, JPG, WebP, PDF, DOCX hoặc TXT.");
        };
    }

    // ── Content extraction ────────────────────────────────────────────────────

    private FileContent readContent(MultipartFile file, String type) {
        return switch (type) {
            case "TEXT" -> readTextFile(file);
            case "PDF" -> readPdfFile(file);
            case "DOCUMENT" -> readDocxFile(file);
            default -> new FileContent(
                    file.getSize(), null, "binary-or-image: " + file.getSize() + " bytes", null, null);
        };
    }

    private FileContent readTextFile(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            return new FileContent(file.getSize(), preview(text), text.length() + " chars", null, null);
        } catch (Exception e) {
            return new FileContent(file.getSize(), null, "read-failed: " + e.getMessage(), null, null);
        }
    }

    private FileContent readPdfFile(MultipartFile file) {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            StringBuilder allText = new StringBuilder();
            int pageCount = doc.getNumberOfPages();

            // Read up to 10 pages or 8000 chars total
            int maxPages = Math.min(pageCount, 10);
            int startPage = 1;

            for (int page = startPage; page <= maxPages && allText.length() < MAX_TEXT_PREVIEW; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc).trim();
                if (!pageText.isBlank()) {
                    allText.append("--- Trang ").append(page).append(" ---\n");
                    allText.append(pageText).append("\n");
                }
            }

            String text = allText.toString();
            String summary = pageCount + " trang, đã đọc " + maxPages + " trang, " + text.length() + " ký tự";
            byte[] renderedPages = hasMeaningfulText(text) ? null : renderPdfForVision(doc);
            return new FileContent(
                    file.getSize(), preview(text), summary,
                    renderedPages, renderedPages == null ? null : "jpeg");
        } catch (Exception e) {
            log.warn("PDF read failed ({}): {}", file.getOriginalFilename(), e.getMessage());
            return new FileContent(
                    file.getSize(), null, "pdf-read-failed: " + e.getMessage(), null, null);
        }
    }

    private FileContent readDocxFile(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            Set<String> sections = new LinkedHashSet<>();
            String extractedText = extractor.getText();
            addTextSection(sections, extractedText);

            // XML text nodes include drawing/text-box content which is not exposed
            // as top-level paragraphs or tables by XWPFDocument.
            addMissingXmlText(sections, extractedText, extractXmlText(doc));
            doc.getHeaderList().forEach(header -> addTextSection(sections, header.getText()));
            doc.getFooterList().forEach(footer -> addTextSection(sections, footer.getText()));

            String text = String.join("\n", sections);
            byte[] embeddedImages = null;
            if (!hasMeaningfulText(text) && !doc.getAllPictures().isEmpty()) {
                List<BufferedImage> images = new ArrayList<>();
                long totalPixels = 0;
                for (XWPFPictureData picture : doc.getAllPictures()) {
                    BufferedImage image = readBoundedImage(picture.getData());
                    if (image != null) {
                        long pixels = (long) image.getWidth() * image.getHeight();
                        if (totalPixels + pixels > MAX_TOTAL_EMBEDDED_IMAGE_PIXELS) break;
                        images.add(image);
                        totalPixels += pixels;
                    }
                    if (images.size() >= 6) break;
                }
                embeddedImages = createVisionContactSheet(images);
            }
            return new FileContent(
                    file.getSize(), preview(text), "docx: " + text.length() + " chars",
                    embeddedImages, embeddedImages == null ? null : "jpeg");
        } catch (NoClassDefFoundError | Exception e) {
            log.warn("DOCX read failed ({}): {}", file.getOriginalFilename(), e.getMessage());
            return new FileContent(file.getSize(), null, "docx-read-unavailable", null, null);
        }
    }

    private void addTextSection(Set<String> sections, String value) {
        if (value == null) return;
        String normalized = value.replace('\u0000', ' ').replaceAll("[ \\t]+", " ")
                .replaceAll("\\R{3,}", "\n\n").trim();
        if (!normalized.isBlank()) sections.add(normalized);
    }

    private String extractXmlText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        try (XmlCursor cursor = document.getDocument().newCursor()) {
            cursor.selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' .//w:t");
            while (cursor.toNextSelection()) {
                String value = cursor.getTextValue();
                if (value != null && !value.isBlank()) text.append(value.trim()).append('\n');
            }
        } catch (Exception e) {
            log.debug("Could not inspect DOCX text-box XML: {}", e.getMessage());
        }
        return text.toString();
    }

    private void addMissingXmlText(Set<String> sections, String extractedText, String xmlText) {
        String searchable = extractedText == null ? "" : extractedText.replaceAll("\\s+", " ");
        for (String line : xmlText.split("\\R")) {
            String normalized = line.replaceAll("\\s+", " ").trim();
            if (!normalized.isBlank() && !searchable.contains(normalized)) sections.add(normalized);
        }
    }

    private byte[] renderPdfForVision(PDDocument doc) {
        try {
            PDFRenderer renderer = new PDFRenderer(doc);
            List<BufferedImage> pages = new ArrayList<>();
            int pageLimit = Math.min(doc.getNumberOfPages(), MAX_VISION_PAGES);
            for (int page = 0; page < pageLimit; page++) {
                float pageWidth = Math.max(1f, doc.getPage(page).getCropBox().getWidth());
                float pageHeight = Math.max(1f, doc.getPage(page).getCropBox().getHeight());
                float maxPageHeight = (float) VISION_MAX_HEIGHT / Math.max(1, pageLimit);
                float scale = Math.min(100f / 72f, Math.min(
                        VISION_MAX_WIDTH / pageWidth,
                        maxPageHeight / pageHeight));
                pages.add(renderer.renderImage(page, scale, ImageType.RGB));
            }
            return createVisionContactSheet(pages);
        } catch (Exception e) {
            log.warn("Could not render scanned PDF for vision: {}", e.getMessage());
            return null;
        }
    }

    private BufferedImage readBoundedImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0 || width > 8000 || height > 8000
                        || pixels > MAX_EMBEDDED_IMAGE_PIXELS) {
                    log.warn("Skipping oversized DOCX image: {}x{}", width, height);
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.debug("Could not decode embedded DOCX image: {}", e.getMessage());
            return null;
        }
    }

    private byte[] createVisionContactSheet(List<BufferedImage> images) {
        if (images == null || images.isEmpty()) return null;
        int widest = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        long totalHeight = images.stream().mapToLong(BufferedImage::getHeight).sum()
                + (long) Math.max(0, images.size() - 1) * 12;
        double scale = Math.min(1d, Math.min(
                (double) VISION_MAX_WIDTH / widest,
                (double) VISION_MAX_HEIGHT / totalHeight));
        int canvasWidth = Math.max(1, (int) Math.ceil(widest * scale));
        int canvasHeight = Math.max(1, images.stream()
                .mapToInt(image -> (int) Math.ceil(image.getHeight() * scale)).sum()
                + Math.max(0, images.size() - 1) * 12);

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, canvasWidth, canvasHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int y = 0;
            for (BufferedImage image : images) {
                int width = Math.max(1, (int) Math.ceil(image.getWidth() * scale));
                int height = Math.max(1, (int) Math.ceil(image.getHeight() * scale));
                graphics.drawImage(image, (canvasWidth - width) / 2, y, width, height, null);
                y += height + 12;
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(canvas, "jpeg", output)) return null;
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_IMAGE_BYTES) {
                log.warn("Rendered document is too large for vision: {} bytes", bytes.length);
                return null;
            }
            return bytes;
        } catch (Exception e) {
            log.warn("Could not encode document preview for vision: {}", e.getMessage());
            return null;
        }
    }

    private String preview(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_TEXT_PREVIEW) return trimmed;
        return trimmed.substring(0, MAX_TEXT_PREVIEW) + "\n... [+" + (trimmed.length() - MAX_TEXT_PREVIEW) + " chars]";
    }

    private boolean hasMeaningfulText(String text) {
        if (text == null || text.isBlank()) return false;
        return text.codePoints().filter(Character::isLetterOrDigit).limit(MIN_READABLE_CHARACTERS)
                .count() >= MIN_READABLE_CHARACTERS;
    }

    private String combineContext(String taskDescription, String extractedText) {
        if (extractedText == null || extractedText.isBlank()) return taskDescription;
        if (taskDescription == null || taskDescription.isBlank()) return extractedText;
        return taskDescription.trim() + "\n\nPartial text extracted from the uploaded file:\n"
                + extractedText.trim();
    }

    private record FileContent(
            long size,
            String preview,
            String summary,
            byte[] visionBytes,
            String visionFormat
    ) {}

    private String buildContext(String fileName, String type, FileContent content,
                                String taskDescription, String extraRequirements) {
        StringBuilder sb = new StringBuilder();

        // File metadata
        sb.append("=== THÔNG TIN FILE ===\n");
        sb.append("Tên file: ").append(fileName).append("\n");
        sb.append("Loại file: ").append(type).append("\n");
        sb.append("Kích thước: ").append(content.size).append(" bytes\n");
        sb.append("Thông tin: ").append(content.summary).append("\n\n");

        // User-provided description
        if (taskDescription != null && !taskDescription.isBlank()) {
            sb.append("=== MÔ TẢ CÔNG VIỆC (do người dùng cung cấp) ===\n")
                    .append(taskDescription.trim()).append("\n\n");
        }

        // Extra requirements
        if (extraRequirements != null && !extraRequirements.isBlank()) {
            sb.append("=== YÊU CẦU BỔ SUNG (do người dùng cung cấp) ===\n")
                    .append(extraRequirements.trim()).append("\n\n");
        }

        // File content
        if (content.preview != null && !content.preview.isBlank()) {
            sb.append("=== NỘI DUNG FILE (trích xuất tự động) ===\n")
                    .append(content.preview).append("\n");
        } else {
            sb.append("=== NỘI DUNG FILE ===\n[Không đọc được nội dung file - chỉ có metadata]\n");
        }

        return sb.toString();
    }

    // ── Gemini attempt ────────────────────────────────────────────────────────

    private CriteriaExtractResponse tryAiExtraction(String fileName, String type, String combinedContext) {
        // Check if context has actual file content
        boolean hasContent = combinedContext.contains("=== NỘI DUNG FILE")
                && !combinedContext.contains("[Không đọc được nội dung file");

        if (!hasContent) {
            log.info("No readable file content, using heuristic fallback for {}", fileName);
            return null;
        }

        try {
            com.taskhub.dto.response.AiCriteriaResponse resp = aiService
                    .suggestCriteriaFromBrief(combinedContext, type, fileName);
            if (resp == null || resp.getSuggestions() == null || resp.getSuggestions().isEmpty()) {
                log.warn("Bedrock returned empty criteria suggestions for {}", fileName);
                return null;
            }
            List<ExtractedCriterion> out = new ArrayList<>();
            for (com.taskhub.dto.response.AiCriteriaResponse.CriteriaSuggestion s : resp.getSuggestions()) {
                String rationale = s.getEvaluationGuide() != null ? s.getEvaluationGuide() : "AI suggestion";
                String criterionText = buildCriterionText(s);
                out.add(ExtractedCriterion.builder()
                        .text(criterionText)
                        .rationale(rationale)
                        .build());
            }
            log.info("Bedrock extracted {} criteria from {}", out.size(), fileName);
            return CriteriaExtractResponse.builder()
                    .fileName(fileName)
                    .detectedType(type)
                    .suggestions(out)
                    .build();
        } catch (Exception e) {
            log.warn("Bedrock criteria extraction failed for {}, falling back to heuristic: {}",
                    fileName, e.getMessage());
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

    private String detectImageFormat(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "jpeg";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        throw TaskHubException.badRequest("Unsupported or invalid image. Use PNG, JPG, GIF or WebP.");
    }

    private ExtractedCriterion criterion(String text, String rationale) {
        return ExtractedCriterion.builder().text(text).rationale(rationale).build();
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

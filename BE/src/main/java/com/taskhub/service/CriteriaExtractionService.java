package com.taskhub.service;

import com.taskhub.dto.response.CriteriaExtractResponse;
import com.taskhub.dto.response.CriteriaExtractResponse.ExtractedCriterion;
import com.taskhub.exception.TaskHubException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service gợi ý criteria từ file brief (heuristic theo loại file).
 * Thuộc module AI, được gọi từ TaskController.
 */
@Service
public class CriteriaExtractionService {

    private static final long MAX_BYTES = 15 * 1024 * 1024;

    /**
     * Trả về danh sách criteria gợi ý dựa trên tên file và content-type.
     */
    public CriteriaExtractResponse extractFromFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw TaskHubException.badRequest("File is required");
        if (file.getSize() > MAX_BYTES)
            throw TaskHubException.badRequest("File too large (max 15 MB)");

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String type = detectType(fileName, file.getContentType());
        List<ExtractedCriterion> suggestions = buildSuggestions(type, fileName);

        return CriteriaExtractResponse.builder()
                .fileName(fileName)
                .detectedType(type)
                .suggestions(suggestions)
                .build();
    }

    private String detectType(String fileName, String contentType) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") || "application/pdf".equals(contentType)) return "PDF";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) return "SPREADSHEET";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))
            return "IMAGE";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "DOCUMENT";
        if (contentType != null && contentType.startsWith("image/")) return "IMAGE";
        return "GENERIC";
    }

    private List<ExtractedCriterion> buildSuggestions(String type, String fileName) {
        List<ExtractedCriterion> list = new ArrayList<>();
        switch (type) {
            case "PDF" -> {
                list.add(criterion(
                        "Giao 1 file PDF ten \"" + baseName(fileName) + "_final.pdf\", toi thieu 5 trang, font Arial 12pt, margin 2.5cm",
                        "Trich tu tai lieu PDF — dinh nghia ro dinh dang va do dai"));
                list.add(criterion(
                        "Noi dung PDF khop 100% voi outline da dinh kem (muc 1–3), khong thieu muc bat buoc",
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
            default -> {
                list.add(criterion(
                        "Giao dung 1 file deliverable theo dinh dang da thong nhat trong brief, kem checklist tu kiem",
                        "File generic — can bo sung so luong va dinh dang"));
            }
        }
        return list;
    }

    private ExtractedCriterion criterion(String text, String rationale) {
        return ExtractedCriterion.builder().text(text).rationale(rationale).build();
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

package com.taskhub.service;

import com.taskhub.dto.response.CriteriaExtractResponse;
import com.taskhub.exception.TaskHubException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriteriaExtractionServiceTest {

    @Mock private TaskHubAiService aiService;
    private CriteriaExtractionService service;

    @BeforeEach
    void setUp() {
        service = new CriteriaExtractionService(aiService);
    }

    @Test
    void invalidDocxReturnsClearErrorInsteadOfFakeCriteria() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "brief.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "this is not a docx package".getBytes(StandardCharsets.UTF_8));

        TaskHubException error = assertThrows(
                TaskHubException.class, () -> service.extractFromFile(file, null, null));

        assertEquals(400, error.getStatus().value());
        assertEquals(
                "File DOCX không hợp lệ hoặc bị hỏng. Hãy mở bằng Word và lưu lại dưới dạng .docx rồi upload lại.",
                error.getMessage());
    }

    @Test
    void docxTextIsExtractedAndSentToTextModel() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(
                    "Bàn giao 3 banner PNG 1920x1080, hệ màu sRGB, không watermark và đúng nội dung brief.");
            document.write(output);
            docx = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "brief.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        CriteriaExtractResponse expected = response("DOCUMENT");
        when(aiService.extractTaskBriefFromText(anyString(), eq("DOCUMENT"), eq("brief.docx"),
                eq("Mô tả hiện tại"), eq("Ưu tiên mobile"))).thenReturn(expected);

        CriteriaExtractResponse actual = service.extractFromFile(
                file, "Mô tả hiện tại", "Ưu tiên mobile");

        assertSame(expected, actual);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(aiService).extractTaskBriefFromText(content.capture(), eq("DOCUMENT"),
                eq("brief.docx"), eq("Mô tả hiện tại"), eq("Ưu tiên mobile"));
        assertTrue(content.getValue().contains("1920x1080"));
    }

    @Test
    void scannedPdfIsRenderedAndSentToVisionModel() throws Exception {
        byte[] imageBytes = createBriefImage("Bàn giao 3 banner PNG 1920x1080");
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(LosslessFactory.createFromImage(
                        document, ImageIO.read(new ByteArrayInputStream(imageBytes))),
                        30, 500, 500, 180);
            }
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", pdfBytes);
        CriteriaExtractResponse expected = response("IMAGE");
        when(aiService.extractTaskBriefFromImage(
                any(byte[].class), eq("jpeg"), eq("scan.pdf"), eq("Mô tả"), eq("Yêu cầu")))
                .thenReturn(expected);

        CriteriaExtractResponse actual = service.extractFromFile(file, "Mô tả", "Yêu cầu");

        assertSame(expected, actual);
        verify(aiService).extractTaskBriefFromImage(
                any(byte[].class), eq("jpeg"), eq("scan.pdf"), eq("Mô tả"), eq("Yêu cầu"));
    }

    @Test
    void imageOnlyDocxUsesEmbeddedPictureWithVisionModel() throws Exception {
        byte[] imageBytes = createBriefImage("Tiêu chí nghiệm thu trong ảnh DOCX");
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().addPicture(
                    new ByteArrayInputStream(imageBytes), Document.PICTURE_TYPE_PNG,
                    "brief.png", Units.toEMU(600), Units.toEMU(200));
            document.write(output);
            docx = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "image-brief.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
        CriteriaExtractResponse expected = response("IMAGE");
        when(aiService.extractTaskBriefFromImage(
                any(byte[].class), eq("jpeg"), eq("image-brief.docx"), any(), any()))
                .thenReturn(expected);

        CriteriaExtractResponse actual = service.extractFromFile(file, null, null);

        assertSame(expected, actual);
        verify(aiService).extractTaskBriefFromImage(
                any(byte[].class), eq("jpeg"), eq("image-brief.docx"), any(), any());
    }

    @Test
    void uploadedImageIsPassedDirectlyToVisionModel() throws Exception {
        byte[] imageBytes = createBriefImage("Bàn giao logo SVG và PNG");
        MockMultipartFile file = new MockMultipartFile(
                "file", "brief.png", "image/png", imageBytes);
        CriteriaExtractResponse expected = response("IMAGE");
        when(aiService.extractTaskBriefFromImage(
                any(byte[].class), eq("png"), eq("brief.png"), any(), any()))
                .thenReturn(expected);

        assertSame(expected, service.extractFromFile(file, null, null));
    }

    private byte[] createBriefImage(String text) throws Exception {
        BufferedImage image = new BufferedImage(900, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, 40, 140);
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private CriteriaExtractResponse response(String detectedType) {
        return CriteriaExtractResponse.builder()
                .detectedType(detectedType)
                .suggestions(List.of(CriteriaExtractResponse.ExtractedCriterion.builder()
                        .text("Bàn giao đúng 3 file theo brief đã cung cấp")
                        .build()))
                .build();
    }
}

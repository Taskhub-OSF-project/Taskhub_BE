package com.taskhub.service;

import com.taskhub.exception.TaskHubException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriteriaExtractionServiceTest {

    private final CriteriaExtractionService service = new CriteriaExtractionService(null);

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
}

package com.taskhub;

import com.taskhub.exception.TaskHubException;
import com.taskhub.util.FileUploadValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadValidatorTests {
    @Test
    void emptyFileFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(TaskHubException.class, () -> FileUploadValidator.validate(file));
    }

    @Test
    void unsupportedContentTypeFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.sh",
                "text/x-shellscript",
                "echo unsafe".getBytes()
        );

        assertThrows(TaskHubException.class, () -> FileUploadValidator.validate(file));
    }

    @Test
    void sanitizeOriginalFileNameRemovesPathTraversalAndDangerousCharacters() {
        String sanitized = FileUploadValidator.sanitizeOriginalFileName("../../bad name @#$%.pdf");

        assertEquals("bad-name-____.pdf", sanitized);
        assertFalse(sanitized.contains(".."));
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains("\\"));
    }
}

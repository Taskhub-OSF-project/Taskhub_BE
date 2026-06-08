package com.taskhub.util;

import com.taskhub.exception.TaskHubException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

public final class FileUploadValidator {
    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip"
    );

    private FileUploadValidator() {
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw TaskHubException.badRequest("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw TaskHubException.badRequest("File size must not exceed 20MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw TaskHubException.badRequest("Unsupported file content type: " + contentType);
        }
    }

    public static String sanitizeOriginalFileName(String originalFileName) {
        String fileName = originalFileName == null ? "file" : originalFileName.trim();
        fileName = fileName.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }

        fileName = fileName.replaceAll("[\\r\\n\\t]", "");
        fileName = fileName.replaceAll("[^A-Za-z0-9._ -]", "_");
        fileName = fileName.replaceAll("\\s+", "-");
        fileName = fileName.replaceAll("\\.+", ".");
        fileName = fileName.replaceAll("^[._-]+", "");

        if (fileName.isBlank()) {
            return "file";
        }
        return fileName.length() > 120 ? fileName.substring(fileName.length() - 120) : fileName;
    }
}

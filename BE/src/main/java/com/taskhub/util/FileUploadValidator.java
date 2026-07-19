package com.taskhub.util;

import com.taskhub.exception.TaskHubException;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileUploadValidator {
    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/x-zip-compressed"
    );

    private static final long MAX_EXPANDED_ARCHIVE_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 2_000;

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
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
            throw TaskHubException.badRequest("Unsupported file content type: " + contentType);
        }

        try {
            validateContentSignature(file, normalizedType);
        } catch (IOException ex) {
            throw TaskHubException.badRequest("Unable to inspect uploaded file");
        }
    }

    private static void validateContentSignature(MultipartFile file, String contentType) throws IOException {
        byte[] header = new byte[16];
        int read;
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            read = input.read(header);
        }
        if (read < 4) throw TaskHubException.badRequest("Uploaded file is incomplete");

        boolean valid = switch (contentType) {
            case "application/pdf" -> startsWith(header, read, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "image/png" -> startsWith(header, read,
                    new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "image/jpeg" -> (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
            case "image/webp" -> read >= 12
                    && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            case "application/zip", "application/x-zip-compressed",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    isZipHeader(header, read);
            default -> false;
        };
        if (!valid) throw TaskHubException.badRequest("File content does not match its declared type");

        if (contentType.contains("zip") || contentType.contains("wordprocessingml")) {
            inspectArchive(file, contentType.contains("wordprocessingml"));
        }
    }

    private static void inspectArchive(MultipartFile file, boolean requireDocxStructure) throws IOException {
        int entries = 0;
        long expandedBytes = 0;
        Set<String> names = new HashSet<>();
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(file.getInputStream()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw TaskHubException.badRequest("Archive contains too many entries");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.indexOf('\0') >= 0) {
                    throw TaskHubException.badRequest("Archive contains an unsafe path");
                }
                names.add(name);
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expandedBytes += count;
                    if (expandedBytes > MAX_EXPANDED_ARCHIVE_BYTES) {
                        throw TaskHubException.badRequest("Expanded archive is too large");
                    }
                }
            }
        }
        if (entries == 0) throw TaskHubException.badRequest("Archive is empty or invalid");
        if (requireDocxStructure
                && (!names.contains("[Content_Types].xml") || !names.contains("word/document.xml"))) {
            throw TaskHubException.badRequest("File is not a valid DOCX document");
        }
    }

    private static boolean isZipHeader(byte[] header, int read) {
        if (read < 4) return false;
        byte[] signature = Arrays.copyOf(header, 4);
        return Arrays.equals(signature, new byte[]{0x50, 0x4b, 0x03, 0x04})
                || Arrays.equals(signature, new byte[]{0x50, 0x4b, 0x05, 0x06})
                || Arrays.equals(signature, new byte[]{0x50, 0x4b, 0x07, 0x08});
    }

    private static boolean startsWith(byte[] value, int valueLength, byte[] prefix) {
        if (valueLength < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) return false;
        }
        return true;
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

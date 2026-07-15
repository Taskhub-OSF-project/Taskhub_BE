package com.taskhub.service;

import com.taskhub.config.SupabaseProperties;
import com.taskhub.dto.response.FileUploadResponse;
import com.taskhub.entity.User;
import com.taskhub.exception.TaskHubException;
import com.taskhub.util.FileUploadValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class SupabaseStorageService implements FileStorageService {
    private final SupabaseProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public FileUploadResponse upload(MultipartFile file, Long taskId, User currentUser) {
        if (taskId == null) {
            throw TaskHubException.badRequest("taskId is required");
        }
        if (currentUser == null || currentUser.getId() == null) {
            throw TaskHubException.forbidden("Authenticated user is required");
        }

        LocalDateTime uploadedAt = LocalDateTime.now();
        String sanitizedFileName = FileUploadValidator.sanitizeOriginalFileName(file.getOriginalFilename());
        String path = "submissions/task-%d/user-%d/%d-%s".formatted(
                taskId,
                currentUser.getId(),
                System.currentTimeMillis(),
                sanitizedFileName
        );

        FileUploadValidator.validate(file);
        if (!isConfigured()) {
            return FileUploadResponse.builder()
                    .fileName(sanitizedFileName)
                    .path(path)
                    .url(null)
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .uploadedAt(uploadedAt)
                    .build();
        }

        try {
            String endpoint = normalizedUrl() + "/storage/v1/object/"
                    + properties.getStorage().getBucket() + "/" + path;

            restClientBuilder.build()
                    .post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceRoleKey())
                    .header("apikey", properties.getServiceRoleKey())
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();

            return FileUploadResponse.builder()
                    .fileName(sanitizedFileName)
                    .path(path)
                    .url(null) // Private bucket: add signed URL endpoint in a later phase.
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .uploadedAt(uploadedAt)
                    .build();
        } catch (RestClientResponseException ex) {
            throw TaskHubException.internalError("Supabase upload failed: HTTP " + ex.getStatusCode().value());
        } catch (IOException | RestClientException ex) {
            throw TaskHubException.internalError("Supabase upload failed");
        }
    }

    @Override
    public String createSignedUrl(String path, Long taskId) {
        validateTaskPath(path, taskId);
        if (!isConfigured()) {
            throw TaskHubException.internalError("Private file storage is not configured");
        }
        try {
            String encodedPath = UriUtils.encodePath(path, StandardCharsets.UTF_8);
            String endpoint = normalizedUrl() + "/storage/v1/object/sign/"
                    + UriUtils.encodePathSegment(properties.getStorage().getBucket(), StandardCharsets.UTF_8)
                    + "/" + encodedPath;
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClientBuilder.build().post().uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getServiceRoleKey())
                    .header("apikey", properties.getServiceRoleKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", 300))
                    .retrieve().body(Map.class);
            Object signed = body == null ? null : body.get("signedURL");
            if (signed == null && body != null) signed = body.get("signedUrl");
            if (signed == null) throw TaskHubException.internalError("Storage did not return a signed URL");
            String value = signed.toString();
            return value.startsWith("http") ? value : normalizedUrl() + "/storage/v1" + value;
        } catch (RestClientResponseException ex) {
            throw TaskHubException.internalError("Unable to create private file URL");
        } catch (RestClientException ex) {
            throw TaskHubException.internalError("Unable to create private file URL");
        }
    }

    private void validateTaskPath(String path, Long taskId) {
        String prefix = "submissions/task-" + taskId + "/";
        if (taskId == null || path == null || !path.startsWith(prefix)
                || path.contains("..") || path.contains("\\") || path.chars().anyMatch(Character::isISOControl)) {
            throw TaskHubException.forbidden("File path is outside the task namespace");
        }
    }

    private boolean isConfigured() {
        return !isBlank(properties.getUrl())
                && !isBlank(properties.getServiceRoleKey())
                && properties.getStorage() != null
                && !isBlank(properties.getStorage().getBucket());
    }

    private String normalizedUrl() {
        return properties.getUrl().replaceAll("/+$", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

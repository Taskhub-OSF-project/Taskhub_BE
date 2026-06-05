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

        FileUploadValidator.validate(file);
        ensureConfigured();

        LocalDateTime uploadedAt = LocalDateTime.now();
        String sanitizedFileName = FileUploadValidator.sanitizeOriginalFileName(file.getOriginalFilename());
        String path = "submissions/task-%d/user-%d/%d-%s".formatted(
                taskId,
                currentUser.getId(),
                System.currentTimeMillis(),
                sanitizedFileName
        );

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

    private void ensureConfigured() {
        if (isBlank(properties.getUrl())) {
            throw TaskHubException.internalError("Missing Supabase config: supabase.url / SUPABASE_URL");
        }
        if (isBlank(properties.getServiceRoleKey())) {
            throw TaskHubException.internalError("Missing Supabase config: supabase.service-role-key / SUPABASE_SERVICE_ROLE_KEY");
        }
        if (properties.getStorage() == null || isBlank(properties.getStorage().getBucket())) {
            throw TaskHubException.internalError("Missing Supabase config: supabase.storage.bucket / SUPABASE_STORAGE_BUCKET");
        }
    }

    private String normalizedUrl() {
        return properties.getUrl().replaceAll("/+$", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

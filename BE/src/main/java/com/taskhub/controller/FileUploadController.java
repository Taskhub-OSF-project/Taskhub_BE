package com.taskhub.controller;

import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.FileUploadResponse;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.TaskRepository;
import com.taskhub.security.AuthUtil;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.enums.Role;
import com.taskhub.service.FileStorageService;
import com.taskhub.util.FileUploadValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {
    private final FileStorageService fileStorageService;
    private final TaskRepository taskRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("taskId") Long taskId) {
        if (taskId == null) {
            throw TaskHubException.badRequest("taskId is required");
        }
        FileUploadValidator.validate(file);
        User current = AuthUtil.getCurrentUser();
        requireTaskFileAccess(taskId, current);

        return ResponseEntity.ok(ApiResponse.ok(
                "File uploaded",
                fileStorageService.upload(file, taskId, current)
        ));
    }

    @GetMapping("/signed-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> signedUrl(
            @RequestParam("taskId") Long taskId, @RequestParam("path") String path) {
        requireTaskFileAccess(taskId, AuthUtil.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Signed URL created",
                Map.of("url", fileStorageService.createSignedUrl(path, taskId))));
    }

    private void requireTaskFileAccess(Long taskId, User current) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> TaskHubException.notFound("Task not found"));
        boolean allowed = current.getRole() == Role.ADMIN
                || task.getHirer().getId().equals(current.getId())
                || (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(current.getId()));
        if (!allowed) throw TaskHubException.forbidden("Only task participants can access files");
    }
}

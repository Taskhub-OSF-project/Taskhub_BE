package com.taskhub.controller;

import com.taskhub.dto.response.ApiResponse;
import com.taskhub.dto.response.FileUploadResponse;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.TaskRepository;
import com.taskhub.security.AuthUtil;
import com.taskhub.service.FileStorageService;
import com.taskhub.util.FileUploadValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
        if (!taskRepository.existsById(taskId)) {
            throw TaskHubException.notFound("Task not found");
        }

        return ResponseEntity.ok(ApiResponse.ok(
                "File uploaded",
                fileStorageService.upload(file, taskId, AuthUtil.getCurrentUser())
        ));
    }
}

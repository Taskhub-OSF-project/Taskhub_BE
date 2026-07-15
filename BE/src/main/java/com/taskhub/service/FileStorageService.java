package com.taskhub.service;

import com.taskhub.dto.response.FileUploadResponse;
import com.taskhub.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    FileUploadResponse upload(MultipartFile file, Long taskId, User currentUser);
    String createSignedUrl(String path, Long taskId);
}

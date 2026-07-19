package com.taskhub.exception;

import com.taskhub.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler chuẩn hóa response lỗi cho toàn bộ API.
 * Thuộc module Exception, gom các lỗi nghiệp vụ/validation.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(401).body(ApiResponse.error("Authentication failed", "UNAUTHORIZED", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiResponse.error("Access denied", "FORBIDDEN", null));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(401).body(ApiResponse.error("Invalid credentials", "UNAUTHORIZED", null));
    }

    /**
     * Bắt TaskHubException để trả đúng HTTP status và errorCode.
     */
    @ExceptionHandler(TaskHubException.class)
    public ResponseEntity<ApiResponse<Object>> handle(TaskHubException ex) {
        ApiResponse<Object> body = ApiResponse.error(ex.getMessage(), ex.getErrorCode(), ex.getDetails());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * Bắt lỗi validate từ @Valid.
     * Trả chi tiết field nào lỗi và lý do.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of(
                        "field", e.getField(),
                        "message", getFriendlyMessage(e.getField(), e.getDefaultMessage())
                ))
                .toList();

        String summary = fieldErrors.stream()
                .map(e -> e.get("message"))
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        ApiResponse<Object> body = ApiResponse.<Object>builder()
                .success(false)
                .message(summary)
                .errorCode("VALIDATION_ERROR")
                .data(fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    private String getFriendlyMessage(String field, String defaultMsg) {
        if (field == null) return defaultMsg;
        return switch (field.toLowerCase()) {
            case "email" -> "Email không hợp lệ. Vui lòng nhập địa chỉ email đúng format (ví dụ: email@example.com)";
            case "password" -> "Mật khẩu không được để trống";
            case "fullname", "fullName", "name" -> "Họ và tên không được để trống";
            case "role" -> "Vai trò không hợp lệ";
            default -> defaultMsg;
        };
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("File size must not exceed 20MB"));
    }

    @ExceptionHandler({
            MultipartException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMultipart(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid multipart request"));
    }

    /**
     * Fallback cho lỗi không lường trước.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled API exception", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.error("Internal server error"));
    }
}

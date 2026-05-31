package com.taskhub.exception;

import com.taskhub.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/**
 * Handler chuẩn hóa response lỗi cho toàn bộ API.
 * Thuộc module Exception, gom các lỗi nghiệp vụ/validation.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiResponse.error(msg));
    }

    /**
     * Fallback cho lỗi không lường trước.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiResponse.error("Internal server error"));
    }
}

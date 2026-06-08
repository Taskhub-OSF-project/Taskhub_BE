package com.taskhub.exception;

import com.taskhub.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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
        ex.printStackTrace();
        return ResponseEntity.internalServerError().body(ApiResponse.error("Internal server error"));
    }
}

package com.taskhub.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

/**
 * Exception nghiệp vụ có kèm HTTP status và errorCode.
 * Thuộc module Exception, dùng thống nhất trong service layer.
 */
@Getter
public class TaskHubException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final Object details;

    public TaskHubException(String message, HttpStatus status) {
        this(message, status, null, null);
    }

    public TaskHubException(String message, HttpStatus status, String errorCode, Object details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * 400 Bad Request cho lỗi input/validation.
     */
    public static TaskHubException badRequest(String msg) {
        return new TaskHubException(msg, HttpStatus.BAD_REQUEST);
    }

    /**
     * 404 Not Found khi không tìm thấy resource.
     */
    public static TaskHubException notFound(String msg) {
        return new TaskHubException(msg, HttpStatus.NOT_FOUND);
    }

    /**
     * 403 Forbidden khi user không đủ quyền.
     */
    public static TaskHubException forbidden(String msg) {
        return new TaskHubException(msg, HttpStatus.FORBIDDEN);
    }

    /**
     * 500 Internal Server Error cho lỗi hệ thống.
     */
    public static TaskHubException internalError(String msg) {
        return new TaskHubException(msg, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 402 Payment Required khi ví không đủ.
     */
    public static TaskHubException insufficientWallet(String msg, Object details) {
        return new TaskHubException(msg, HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_WALLET", details);
    }

    /**
     * 400 Invalid criteria khi AI validation fail.
     */
    public static TaskHubException invalidCriteria(String msg, Object details) {
        return new TaskHubException(msg, HttpStatus.BAD_REQUEST, "INVALID_CRITERIA", details);
    }
}

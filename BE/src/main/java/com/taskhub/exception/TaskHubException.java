package com.taskhub.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

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

    public static TaskHubException badRequest(String msg) {
        return new TaskHubException(msg, HttpStatus.BAD_REQUEST);
    }

    public static TaskHubException notFound(String msg) {
        return new TaskHubException(msg, HttpStatus.NOT_FOUND);
    }

    public static TaskHubException forbidden(String msg) {
        return new TaskHubException(msg, HttpStatus.FORBIDDEN);
    }

    public static TaskHubException internalError(String msg) {
        return new TaskHubException(msg, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static TaskHubException insufficientWallet(String msg, Object details) {
        return new TaskHubException(msg, HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_WALLET", details);
    }

    public static TaskHubException invalidCriteria(String msg, Object details) {
        return new TaskHubException(msg, HttpStatus.BAD_REQUEST, "INVALID_CRITERIA", details);
    }
}

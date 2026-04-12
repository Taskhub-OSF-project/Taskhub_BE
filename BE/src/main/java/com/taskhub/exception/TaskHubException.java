package com.taskhub.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class TaskHubException extends RuntimeException {
    private final HttpStatus status;

    public TaskHubException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public static TaskHubException badRequest(String msg) { return new TaskHubException(msg, HttpStatus.BAD_REQUEST); }
    public static TaskHubException notFound(String msg) { return new TaskHubException(msg, HttpStatus.NOT_FOUND); }
    public static TaskHubException forbidden(String msg) { 
        return new TaskHubException(msg, HttpStatus.FORBIDDEN); 
    }
    
    // Add missing internalError method
    public static TaskHubException internalError(String msg) { 
        return new TaskHubException(msg, HttpStatus.INTERNAL_SERVER_ERROR); 
    }
}

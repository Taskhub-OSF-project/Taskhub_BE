package com.taskhub.entity;// Thay đổi từ record đơn giản thành class đầy đủ

import java.util.List;

public  class ValidationResult {
    private final boolean valid;
    private final String message;
    private final List<CriteriaValidationDetail> details;
    
    public ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
        this.details = List.of();
    }
    
    public ValidationResult(boolean valid, String message, List<CriteriaValidationDetail> details) {
        this.valid = valid;
        this.message = message;
        this.details = details;
    }
    

}

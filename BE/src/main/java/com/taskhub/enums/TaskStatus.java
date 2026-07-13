package com.taskhub.enums;

public enum TaskStatus {
    DRAFT,
    LOCKED,
    ESCROW_FUNDED,
    ACTIVE,
    IN_PROGRESS,
    SUBMITTED,
    COMPLETED,
    DISPUTED,
    REMOVAL_REQUESTED; // Người đăng job yêu cầu gỡ job

    public boolean canTransitionTo(TaskStatus next) {
        return switch (this) {
            case DRAFT -> next == LOCKED;
            case LOCKED -> next == ESCROW_FUNDED;
            case ESCROW_FUNDED -> next == ACTIVE;
            case ACTIVE -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == SUBMITTED;
            case SUBMITTED -> next == COMPLETED || next == DISPUTED || next == IN_PROGRESS;
            case DISPUTED -> next == IN_PROGRESS || next == LOCKED || next == COMPLETED;
            case REMOVAL_REQUESTED -> next == DRAFT || next == ACTIVE || next == COMPLETED || next == LOCKED;
            case COMPLETED -> false;
        };
    }
}

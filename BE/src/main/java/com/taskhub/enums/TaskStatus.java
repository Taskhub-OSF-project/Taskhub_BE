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
            case DRAFT -> next == LOCKED || next == REMOVAL_REQUESTED;
            case LOCKED -> next == ESCROW_FUNDED || next == REMOVAL_REQUESTED;
            case ESCROW_FUNDED -> next == ACTIVE || next == REMOVAL_REQUESTED;
            case ACTIVE -> next == IN_PROGRESS || next == REMOVAL_REQUESTED;
            case IN_PROGRESS -> next == SUBMITTED || next == REMOVAL_REQUESTED;
            case SUBMITTED -> next == COMPLETED || next == DISPUTED || next == IN_PROGRESS
                    || next == REMOVAL_REQUESTED;
            case DISPUTED -> next == IN_PROGRESS || next == LOCKED || next == COMPLETED;
            case REMOVAL_REQUESTED -> next == DRAFT || next == LOCKED || next == ESCROW_FUNDED
                    || next == ACTIVE || next == IN_PROGRESS || next == SUBMITTED;
            case COMPLETED -> false;
        };
    }
}

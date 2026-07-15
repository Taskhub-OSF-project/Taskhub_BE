package com.taskhub.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusRemovalTransitionTest {

    @Test
    void activeTaskCanEnterRemovalReviewAndReturnToOriginalStatus() {
        assertTrue(TaskStatus.ACTIVE.canTransitionTo(TaskStatus.REMOVAL_REQUESTED));
        assertTrue(TaskStatus.REMOVAL_REQUESTED.canTransitionTo(TaskStatus.ACTIVE));
    }

    @Test
    void allCancelableStatesCanEnterAndRestoreFromRemovalReview() {
        for (TaskStatus status : new TaskStatus[]{
                TaskStatus.DRAFT, TaskStatus.LOCKED, TaskStatus.ESCROW_FUNDED,
                TaskStatus.ACTIVE, TaskStatus.IN_PROGRESS, TaskStatus.SUBMITTED}) {
            assertTrue(status.canTransitionTo(TaskStatus.REMOVAL_REQUESTED), status::name);
            assertTrue(TaskStatus.REMOVAL_REQUESTED.canTransitionTo(status), status::name);
        }
        assertFalse(TaskStatus.COMPLETED.canTransitionTo(TaskStatus.REMOVAL_REQUESTED));
        assertFalse(TaskStatus.DISPUTED.canTransitionTo(TaskStatus.REMOVAL_REQUESTED));
    }
}

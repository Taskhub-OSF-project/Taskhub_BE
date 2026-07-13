-- Task Removal Request table for job removal workflow
CREATE TABLE IF NOT EXISTS task_removal_requests (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    requested_by BIGINT NOT NULL REFERENCES users(id),
    removal_reason VARCHAR(50) NOT NULL,
    reason_description TEXT,
    task_status_at_request VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ai_validation_result TEXT,
    ai_recommendation VARCHAR(50),
    admin_id BIGINT REFERENCES users(id),
    admin_notes TEXT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_removal_reason CHECK (removal_reason IN ('DUPLICATE', 'NO_LONGER_NEEDED', 'BUDGET_ISSUES', 'FOUND_BETTER_FREELANCER', 'PROJECT_CANCELLED', 'MISPOSTED', 'OTHER')),
    CONSTRAINT chk_removal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_task_removal_task_id ON task_removal_requests(task_id);
CREATE INDEX IF NOT EXISTS idx_task_removal_status ON task_removal_requests(status);
CREATE INDEX IF NOT EXISTS idx_task_removal_requested_by ON task_removal_requests(requested_by);
CREATE INDEX IF NOT EXISTS idx_task_removal_created_at ON task_removal_requests(created_at DESC);

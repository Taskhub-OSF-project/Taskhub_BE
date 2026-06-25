ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN;

UPDATE users
SET email_verified = FALSE
WHERE email_verified IS NULL;

ALTER TABLE users
    ALTER COLUMN email_verified SET DEFAULT FALSE,
    ALTER COLUMN email_verified SET NOT NULL;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS body TEXT;

UPDATE notifications
SET body = COALESCE(body, message, title, '')
WHERE body IS NULL;

ALTER TABLE notifications
    ALTER COLUMN body SET DEFAULT '',
    ALTER COLUMN body SET NOT NULL;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS link VARCHAR(300),
    ADD COLUMN IF NOT EXISTS related_id BIGINT;

UPDATE notifications
SET link = LEFT(action_url, 300)
WHERE link IS NULL
  AND action_url IS NOT NULL;

UPDATE notifications
SET related_id = task_id
WHERE related_id IS NULL
  AND task_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_read
    ON notifications(user_id, is_read);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications(user_id, created_at);

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS submission_ai_result_json TEXT,
    ADD COLUMN IF NOT EXISTS latest_precheck_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS precheck_student_id BIGINT,
    ADD COLUMN IF NOT EXISTS precheck_can_submit BOOLEAN,
    ADD COLUMN IF NOT EXISTS precheck_submitted_file_paths_json TEXT,
    ADD COLUMN IF NOT EXISTS revision_count INT,
    ADD COLUMN IF NOT EXISTS dispute_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS dispute_description TEXT,
    ADD COLUMN IF NOT EXISTS dispute_ai_report_json TEXT,
    ADD COLUMN IF NOT EXISTS applicant_count INT;

UPDATE tasks
SET revision_count = 0
WHERE revision_count IS NULL;

UPDATE tasks
SET applicant_count = 0
WHERE applicant_count IS NULL;

ALTER TABLE tasks
    ALTER COLUMN revision_count SET DEFAULT 0,
    ALTER COLUMN revision_count SET NOT NULL,
    ALTER COLUMN applicant_count SET DEFAULT 0,
    ALTER COLUMN applicant_count SET NOT NULL;

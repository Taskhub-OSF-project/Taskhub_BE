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

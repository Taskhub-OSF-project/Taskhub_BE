ALTER TABLE otp_tokens
    ALTER COLUMN code TYPE VARCHAR(64);

ALTER TABLE otp_tokens
    ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0;

-- Existing rows contain plaintext six-digit codes and must never remain usable.
UPDATE otp_tokens
SET used = TRUE
WHERE used = FALSE;

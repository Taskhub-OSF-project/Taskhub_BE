-- ============================================================
-- V2__phone_unique_and_otp_tokens.sql
-- Adds UNIQUE constraint on phone column and OTP token table
-- ============================================================

-- Make phone column unique (may fail if duplicates exist — clean data first)
ALTER TABLE users ADD CONSTRAINT uk_users_phone UNIQUE (phone);

CREATE TABLE otp_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL,
    code        VARCHAR(6)   NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_otp_phone_type_unused UNIQUE (phone, type, used)
);
CREATE INDEX idx_otp_phone ON otp_tokens(phone);

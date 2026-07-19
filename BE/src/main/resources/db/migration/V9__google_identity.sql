ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS provider_subject VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_provider_subject
    ON users (auth_provider, provider_subject)
    WHERE provider_subject IS NOT NULL;

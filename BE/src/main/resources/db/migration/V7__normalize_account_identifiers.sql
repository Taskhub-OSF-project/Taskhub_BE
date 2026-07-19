-- Case-insensitive email identity and one-account-per-phone recovery invariant.
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower
    ON users (LOWER(TRIM(email)));

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_phone_nonblank
    ON users (phone)
    WHERE phone IS NOT NULL AND TRIM(phone) <> '';

-- Case-insensitive email identity and one-account-per-phone recovery invariant.
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower
    ON users (LOWER(TRIM(email)));

-- Legacy/demo accounts may share the same phone number. Keep the phone on the
-- oldest account and clear it from later duplicates before enforcing the
-- recovery invariant. Accounts and all related data remain intact.
UPDATE users
SET phone = NULLIF(TRIM(phone), '')
WHERE phone IS DISTINCT FROM NULLIF(TRIM(phone), '');

WITH ranked_phones AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY phone
               ORDER BY created_at ASC, id ASC
           ) AS duplicate_rank
    FROM users
    WHERE phone IS NOT NULL
)
UPDATE users AS target
SET phone = NULL
FROM ranked_phones AS ranked
WHERE target.id = ranked.id
  AND ranked.duplicate_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_phone_nonblank
    ON users (phone)
    WHERE phone IS NOT NULL;

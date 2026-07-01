-- ═══════════════════════════════════════════════════════════════════
-- V3: Fix `notifications.message` NOT NULL mismatch with JPA entity
-- ────────────────────────────────────────────────────────────────
-- The JPA entity only writes the `body` column. Older DBs still have
-- a legacy `message` column with NOT NULL but no default.  Inserts
-- therefore fail with:
--   ERROR: null value in column "message" of relation "notifications"
--   violates not-null constraint
--
-- Strategy:
--   1. Backfill any NULL `message` rows from `body` (or title).
--   2. Convert `message` to a STORED generated column that mirrors
--      `body` (Postgres 12+). This eliminates any drift permanently
--      and removes the need to populate the column from JPA.
--   3. On older Postgres, fall back to default + NOT NULL.

UPDATE notifications
SET message = COALESCE(message, body, title, '')
WHERE message IS NULL;

DO $$
BEGIN
    BEGIN
        -- Convert to generated column (Postgres 12+).
        ALTER TABLE notifications
            ALTER COLUMN message DROP DEFAULT,
            ALTER COLUMN message SET GENERATED ALWAYS AS (COALESCE(body, '')) STORED;
    EXCEPTION
        WHEN feature_not_supported THEN
            -- Older Postgres: set a default so inserts always satisfy
            -- NOT NULL even without an explicit value.
            ALTER TABLE notifications
                ALTER COLUMN message SET DEFAULT '';
    END;
END $$;
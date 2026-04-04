-- ============================================================
-- V22__pro_waitlist.sql
--
-- Creates the pro_waitlist table used to collect email addresses
-- of users interested in the PRO plan before its launch.
--
-- Key design decisions:
--   - email is UNIQUE so duplicate inserts are rejected at the DB
--     level as a safety net (the service layer checks existence
--     first and returns 200 to avoid a 409 surface to the caller).
--   - notified_at is NULL until the PRO launch notification email
--     is dispatched, making it easy to batch-select unsent rows.
--   - created_at defaults to NOW() so rows inserted directly via
--     SQL tools (e.g. superadmin backfill) still get a timestamp.
-- ============================================================

CREATE TABLE IF NOT EXISTS pro_waitlist (
    id           VARCHAR(255)             NOT NULL PRIMARY KEY,
    email        VARCHAR(255)             NOT NULL UNIQUE,
    language     VARCHAR(5),
    source       VARCHAR(50),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    notified_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_pro_waitlist_email
    ON pro_waitlist (email);

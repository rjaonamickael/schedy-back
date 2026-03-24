-- ============================================================
-- V4__pointage_timestamptz_and_constraints.sql
-- B-H20: Migrate pointage.horodatage from TIMESTAMP to TIMESTAMPTZ
-- B-M20: Add UNIQUE constraint on app_user.refresh_token
-- ============================================================

-- B-H20 — Fix pointage.horodatage timezone support for multi-country
ALTER TABLE pointage
    ALTER COLUMN horodatage TYPE TIMESTAMP WITH TIME ZONE;

-- B-M20 — Ensure refresh tokens are unique (prevent theoretical collision)
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_refresh_token
    ON app_user (refresh_token)
    WHERE refresh_token IS NOT NULL;

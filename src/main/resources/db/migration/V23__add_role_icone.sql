-- ============================================================
-- V23__add_role_icone.sql
--
-- Adds an optional icon key to the role table.
-- The icon key is a frontend-resolved string (e.g. "chef-hat").
-- Rendering is 100% client-side; the backend only persists the key.
--
-- Design decisions:
--   - Nullable: existing roles keep their behaviour unchanged.
--   - VARCHAR(50): generous enough for any icon slug,
--     yet prevents garbage data from unbounded client input.
--   - CHECK constraint enforces slug format at the DB level,
--     independent of application validation (defence in depth).
-- ============================================================

ALTER TABLE role
    ADD COLUMN IF NOT EXISTS icone VARCHAR(50)
    CONSTRAINT chk_role_icone_format
        CHECK (icone ~ '^[a-z0-9][a-z0-9-]*[a-z0-9]$' OR icone IS NULL);

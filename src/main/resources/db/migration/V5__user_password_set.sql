-- V5: Track whether a user has set their password (invitation flow).
-- Default false for existing users; ADMIN/SUPERADMIN accounts created with a known password get true.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_set BOOLEAN NOT NULL DEFAULT false;

-- Mark existing ADMIN and SUPERADMIN accounts as having a set password
UPDATE app_user SET password_set = true WHERE role IN ('ADMIN', 'SUPERADMIN');

-- V6: TOTP-based Two-Factor Authentication
-- Adds 2FA fields to app_user and creates the recovery codes table.

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS totp_secret_encrypted VARCHAR(512);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS totp_last_used_otp VARCHAR(6);

CREATE TABLE IF NOT EXISTS totp_recovery_code (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    code_hash   VARCHAR(64) NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_recovery_user ON totp_recovery_code(user_id);

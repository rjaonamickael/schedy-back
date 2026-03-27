-- V7: Email-based 2FA code (sent on login when 2FA is enabled)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email_2fa_code_hash VARCHAR(64);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email_2fa_code_expires_at TIMESTAMPTZ;

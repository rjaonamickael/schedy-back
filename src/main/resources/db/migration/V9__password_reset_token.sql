-- V9: Add password reset token support to app_user for the forgot-password flow
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(64);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS password_reset_token_expires_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_password_reset_token
    ON app_user (password_reset_token)
    WHERE password_reset_token IS NOT NULL;

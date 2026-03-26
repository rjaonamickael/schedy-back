-- V3: Add invitation token support to app_user for secure password creation flow
ALTER TABLE app_user ADD COLUMN invitation_token VARCHAR(64);
ALTER TABLE app_user ADD COLUMN invitation_token_expires_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_invitation_token
    ON app_user (invitation_token)
    WHERE invitation_token IS NOT NULL;

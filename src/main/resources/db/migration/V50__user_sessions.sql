-- V50 — Multi-device refresh-token sessions
--
-- Replaces the single `app_user.refresh_token` slot (one device only) with a
-- dedicated `user_session` table so the same account can stay authenticated on
-- multiple devices simultaneously (laptop + mobile, bureau + kiosk, etc.).
--
-- Rotation semantics are preserved: each successful /refresh creates a new row
-- and deletes the presented one. Per-user cap is enforced in the service layer
-- (FIFO eviction beyond MAX_SESSIONS_PER_USER).
--
-- The legacy `app_user.refresh_token` column is LEFT IN PLACE for rollback
-- safety during the post-beta stabilisation window. It is set to NULL by the
-- service layer on every login/refresh/logout and will be dropped in a later
-- migration (V51+) once the multi-session path is confirmed stable in prod.
CREATE TABLE IF NOT EXISTS user_session (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL
                 REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL,
    user_agent   VARCHAR(512),
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_user_session_token_hash UNIQUE (token_hash)
);

-- Primary lookup: refresh endpoint validates the presented cookie by hash.
CREATE INDEX IF NOT EXISTS idx_user_session_token_hash
    ON user_session (token_hash);

-- Secondary lookup: list/count/delete all sessions of a given user (FIFO
-- eviction, logout-all on password change, admin revocation).
CREATE INDEX IF NOT EXISTS idx_user_session_user_id
    ON user_session (user_id);

-- Maintenance: scheduled cleanup job removes rows where expires_at < NOW().
CREATE INDEX IF NOT EXISTS idx_user_session_expires_at
    ON user_session (expires_at);

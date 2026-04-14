-- V37: PIN audit log + pin_generated_at/pin_version on employe
--
-- Background
-- ----------
-- Adds an audit trail for every PIN lifecycle event (creation, regeneration,
-- printing, revocation) on Employe.pin. Required for Quebec Law 25 / PIPEDA
-- compliance: an admin must be able to answer "who touched this employee's
-- credentials, when, and why".
--
-- Two new columns on `employe`:
--   * pin_generated_at — UTC timestamp of the most recent PIN write
--                        (NULL for legacy rows pre-V37, backfilled lazily on
--                        next regeneration)
--   * pin_version       — monotonic counter incremented on every regeneration.
--                        Printed on the kiosk card so admins can match a
--                        physical card against the current PIN version.
--
-- Audit log table `pin_audit_log`:
--   * employe_id     — FK to employe(id), cascade delete with the employee
--   * admin_user_id  — NULLABLE: NULL for system-triggered events
--                      (rotation scheduler, batch ops without explicit author)
--   * action         — GENERATE | REGENERATE_INDIVIDUAL | REGENERATE_CASCADE
--                      | PRINT | REVOKE
--   * source         — ADMIN_UI | AUTO_ROTATION | BATCH_OP
--   * old_pin_hash / new_pin_hash — SHA-256 hashes ONLY (never plaintext);
--                      nullable for first GENERATE (no old) and PRINT (no new)
--   * motif          — optional admin-supplied reason
--                      ("compromised card", "termination", ...)
--   * timestamp      — server time of the event
--   * organisation_id — denormalised tenant key for fast org-scoped queries
--
-- Indexes:
--   * (employe_id, timestamp DESC) — recent events for one employee (UI)
--   * (organisation_id, timestamp DESC) — org-wide audit feed (compliance)

ALTER TABLE employe ADD COLUMN IF NOT EXISTS pin_generated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE employe ADD COLUMN IF NOT EXISTS pin_version INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN employe.pin_generated_at IS
    'UTC timestamp of the most recent PIN write (creation or regeneration). '
    'NULL for legacy rows that pre-date V37; backfilled on next PIN update.';

COMMENT ON COLUMN employe.pin_version IS
    'Monotonic counter incremented on every PIN regeneration. Printed on the '
    'kiosk card so admins can match a physical card against the current version.';

CREATE TABLE IF NOT EXISTS pin_audit_log (
    id              VARCHAR(36) PRIMARY KEY,
    employe_id      VARCHAR(36) NOT NULL,
    admin_user_id   VARCHAR(36),
    action          VARCHAR(40) NOT NULL,
    source          VARCHAR(40) NOT NULL,
    old_pin_hash    VARCHAR(64),
    new_pin_hash    VARCHAR(64),
    motif           TEXT,
    timestamp       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    organisation_id VARCHAR(36) NOT NULL,
    CONSTRAINT fk_pin_audit_employe FOREIGN KEY (employe_id)
        REFERENCES employe(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pin_audit_log_employe_ts
    ON pin_audit_log (employe_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_pin_audit_log_org_ts
    ON pin_audit_log (organisation_id, timestamp DESC);

COMMENT ON TABLE pin_audit_log IS
    'Audit trail for kiosk PIN lifecycle events. SHA-256 hashes only, no '
    'plaintext. Required for Law 25 (Quebec) / PIPEDA (Canada) right-of-access.';

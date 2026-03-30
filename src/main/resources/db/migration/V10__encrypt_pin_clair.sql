-- V10: Encrypt pinClair — OWASP A02 fix
--
-- Background
-- ----------
-- Prior to this migration, pin_clair stored the employee's kiosk PIN in plain text.
-- From application version V10 onward, new and updated PINs are stored as
-- AES-256-GCM encrypted blobs (Base64-encoded, IV prepended) using the same key
-- as the TOTP secret encryption (TOTP_ENCRYPTION_KEY env var).
--
-- This migration:
--   1. Widens the column to TEXT to accommodate the longer encrypted blob
--      (old VARCHAR(255) is already wide enough, but TEXT is cleaner long-term).
--   2. Adds a nullable boolean flag `pin_clair_encrypted` so the application can
--      distinguish pre-migration (plaintext) rows from post-migration (encrypted) rows
--      during the backward-compatibility grace period.
--   3. Sets the flag to FALSE for all existing rows so they are known to be plaintext.
--      The application decryption path already handles this: if decryption fails it
--      falls back to returning the raw value and logs a warning.
--
-- Re-encryption of existing rows
-- --------------------------------
-- Run the one-time re-encryption script (tools/migrate-pin-clair.sh) or reset each
-- employee's PIN through the admin UI. Once all rows have pin_clair_encrypted = TRUE
-- the backward-compatibility fallback in EmployeService.getDecryptedPin() can be removed.

ALTER TABLE employe ALTER COLUMN pin_clair TYPE TEXT;

ALTER TABLE employe ADD COLUMN IF NOT EXISTS pin_clair_encrypted BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark existing rows as NOT yet encrypted so the application knows they are plaintext
UPDATE employe SET pin_clair_encrypted = FALSE WHERE pin_clair IS NOT NULL;

-- Future rows written by the application will be inserted with pin_clair_encrypted = TRUE
-- (enforced at the application layer — no DB trigger needed).

COMMENT ON COLUMN employe.pin_clair IS
    'AES-256-GCM encrypted kiosk PIN (Base64 IV||ciphertext). '
    'See pin_clair_encrypted for migration status. NEVER store plaintext after V10.';

COMMENT ON COLUMN employe.pin_clair_encrypted IS
    'TRUE = pin_clair contains an AES-256-GCM blob. '
    'FALSE = pre-V10 plaintext row, must be re-encrypted on next PIN update.';

-- ============================================================
-- V5__pointage_code_pin_hash.sql
-- B-M19: Add pinHash column for SHA-256 indexed lookup on pointage_code
-- ============================================================

ALTER TABLE pointage_code ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_pointage_code_pin_hash_actif
    ON pointage_code (pin_hash, actif);

-- V40 — Clock-in security: creneau-guard tolerance windows.
--
-- Adds two tolerance columns to parametres so employees may clock in
-- slightly before their shift starts and clock out slightly after it ends
-- without the kiosk creneau guard rejecting them. Default 30 minutes on
-- each side — admins can tighten or widen per org via the settings UI.
ALTER TABLE parametres
    ADD COLUMN IF NOT EXISTS tolerance_avant_shift_minutes integer NOT NULL DEFAULT 30,
    ADD COLUMN IF NOT EXISTS tolerance_apres_shift_minutes integer NOT NULL DEFAULT 30;

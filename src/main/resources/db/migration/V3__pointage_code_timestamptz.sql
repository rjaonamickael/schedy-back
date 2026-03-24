-- ============================================================
-- V3__pointage_code_timestamptz.sql
-- B-M9: Migrate pointage_code validFrom/validTo from TIMESTAMP to TIMESTAMPTZ
-- PostgreSQL auto-converts assuming server timezone for existing rows.
-- ============================================================

ALTER TABLE pointage_code
    ALTER COLUMN valid_from TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN valid_to   TYPE TIMESTAMP WITH TIME ZONE;

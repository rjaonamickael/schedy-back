-- V18: Add optimistic locking version columns to high-contention entities
-- Corresponds to @Version fields added to DemandeConge, AbsenceImprevue, Pause, RegistrationRequest

ALTER TABLE demande_conge       ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE absence_imprevue    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE pause               ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE registration_request ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

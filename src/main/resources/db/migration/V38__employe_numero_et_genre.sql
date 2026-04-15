-- V38 : add numero_employe (alphanumeric HR number) and genre
-- (HOMME / FEMME / AUTRE) to the employe table.
--
-- Design notes :
--   * Both columns are NULLABLE — legacy rows created before this migration
--     stay null until someone edits them through the form. Required-ness
--     is not a backend rule ; the UI decides whether to prompt.
--   * numero_employe is unique WITHIN an organisation, not globally. Two
--     orgs may reuse the same "EMP001" safely because the partial unique
--     index scopes by organisation_id.
--   * Pattern validation (letters + digits only) lives at the DTO layer
--     via @Pattern ; no CHECK constraint here so that the DB rule stays
--     flexible if the front later wants to allow, e.g., hyphens.
--   * genre is stored as VARCHAR so Jackson/JPA can round-trip the enum
--     via @Enumerated(EnumType.STRING) — stable across enum reorderings.

ALTER TABLE employe
    ADD COLUMN IF NOT EXISTS numero_employe VARCHAR(32);

ALTER TABLE employe
    ADD COLUMN IF NOT EXISTS genre VARCHAR(16);

-- Partial unique index : only applied when numero_employe is NOT NULL, so
-- multiple rows without an HR number (the default) don't collide with each
-- other.
CREATE UNIQUE INDEX IF NOT EXISTS idx_employe_numero_org_unique
    ON employe (organisation_id, numero_employe)
    WHERE numero_employe IS NOT NULL;

-- Value guard : keep the DB from accepting something outside the enum if
-- a direct SQL insert slips past the DTO validation. We allow NULL through
-- so the column stays optional.
ALTER TABLE employe
    ADD CONSTRAINT chk_employe_genre
    CHECK (genre IS NULL OR genre IN ('HOMME', 'FEMME', 'AUTRE'));

-- V36: Wire pause.pointage_entree_id / pointage_sortie_id to the pointage table
-- via ON DELETE SET NULL. Layer 3 auto-detection in PauseService now populates
-- these columns; a foreign key guards referential integrity without cascading
-- a pointage deletion into audit loss.
--
-- Design notes :
--   * ON DELETE SET NULL preserves the Pause row even if a manager deletes
--     the underlying pointage. The pause becomes an orphan (both refs NULL)
--     and the manager can clean it up via DELETE /api/v1/pauses/{id}.
--   * Indexes on the two FK columns speed up the duplicate-guard lookup
--     used by PauseService.detectFromPointage (findByPointageEntreeIdAnd
--     PointageSortieId).
--   * Defensive NULL-out : any orphan references created by prior dev data
--     are scrubbed before the constraint is added, so the migration cannot
--     fail on legacy rows.

-- 1. Scrub orphans (defensive — in fresh prod dbs this is a no-op)
UPDATE pause SET pointage_entree_id = NULL
  WHERE pointage_entree_id IS NOT NULL
    AND pointage_entree_id NOT IN (SELECT id FROM pointage);

UPDATE pause SET pointage_sortie_id = NULL
  WHERE pointage_sortie_id IS NOT NULL
    AND pointage_sortie_id NOT IN (SELECT id FROM pointage);

-- 2. Add the foreign keys
ALTER TABLE pause
    ADD CONSTRAINT fk_pause_pointage_entree
    FOREIGN KEY (pointage_entree_id)
    REFERENCES pointage(id)
    ON DELETE SET NULL;

ALTER TABLE pause
    ADD CONSTRAINT fk_pause_pointage_sortie
    FOREIGN KEY (pointage_sortie_id)
    REFERENCES pointage(id)
    ON DELETE SET NULL;

-- 3. Indexes for the duplicate-guard lookup
CREATE INDEX IF NOT EXISTS idx_pause_pointage_entree
    ON pause (pointage_entree_id)
    WHERE pointage_entree_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pause_pointage_sortie
    ON pause (pointage_sortie_id)
    WHERE pointage_sortie_id IS NOT NULL;

-- ============================================================
-- V2__add_pin_hash_and_constraints.sql
-- B-H10 : add pinHash column + covering index
-- B-H11 : add FK constraints between domain tables
-- B-L7  : add composite index on pointage_code (site_id, actif)
-- ============================================================

-- ============================================================
-- B-H10 — pinHash column on employe
-- ============================================================
ALTER TABLE employe ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_employe_pin_hash
    ON employe (pin_hash, organisation_id);

-- ============================================================
-- B-H11 — Foreign key constraints between domain tables
-- ============================================================

-- pointage.employe_id → employe.id  (CASCADE: safe to remove pointages when employee is deleted)
DO $$ BEGIN
    ALTER TABLE pointage
        ADD CONSTRAINT fk_pointage_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- creneau_assigne.employe_id → employe.id  (CASCADE: safe to cascade shifts)
DO $$ BEGIN
    ALTER TABLE creneau_assigne
        ADD CONSTRAINT fk_creneau_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- creneau_assigne.site_id → site.id  (CASCADE: if site is deleted, its shifts go too)
DO $$ BEGIN
    ALTER TABLE creneau_assigne
        ADD CONSTRAINT fk_creneau_site
        FOREIGN KEY (site_id) REFERENCES site (id)
        ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- demande_conge.employe_id → employe.id  (RESTRICT: explicit cleanup required)
DO $$ BEGIN
    ALTER TABLE demande_conge
        ADD CONSTRAINT fk_demande_conge_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- demande_conge.type_conge_id → type_conge.id  (RESTRICT: explicit cleanup required)
DO $$ BEGIN
    ALTER TABLE demande_conge
        ADD CONSTRAINT fk_demande_conge_type
        FOREIGN KEY (type_conge_id) REFERENCES type_conge (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- banque_conge.employe_id → employe.id  (RESTRICT: explicit cleanup required)
DO $$ BEGIN
    ALTER TABLE banque_conge
        ADD CONSTRAINT fk_banque_conge_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- banque_conge.type_conge_id → type_conge.id  (RESTRICT: explicit cleanup required)
DO $$ BEGIN
    ALTER TABLE banque_conge
        ADD CONSTRAINT fk_banque_conge_type
        FOREIGN KEY (type_conge_id) REFERENCES type_conge (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- exigence.site_id → site.id  (RESTRICT: explicit cleanup required before removing a site)
DO $$ BEGIN
    ALTER TABLE exigence
        ADD CONSTRAINT fk_exigence_site
        FOREIGN KEY (site_id) REFERENCES site (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ============================================================
-- B-L7 — Composite index on pointage_code (site_id, actif)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_pointage_code_site_actif
    ON pointage_code (site_id, actif);

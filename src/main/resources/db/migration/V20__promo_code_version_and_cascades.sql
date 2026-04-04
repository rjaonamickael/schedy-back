-- ============================================================
-- V20: PromoCode optimistic locking + ElementCollection CASCADE deletes
--
-- CRIT-02: Add @Version column to promo_code to prevent race condition
--          on currentUses increment when two concurrent applyPromoCode
--          calls both pass the maxUses check.
--
-- CRIT-03: Add ON DELETE CASCADE to all @ElementCollection FK constraints
--          that feed off employe, exigence, and parametres parent tables.
--          Without CASCADE, JPQL bulk deletes in deleteOrganisation() leave
--          orphan rows in the collection tables.
--
-- Note: parametres_pause_fixe_jours and parametres_regles_pause already
--       have ON DELETE CASCADE via inline REFERENCES in V16 — no change needed.
--       absence_creneau_impacte already has ON DELETE CASCADE in V8 — verified.
-- ============================================================

-- ---------------------------------------------------------------
-- CRIT-02: optimistic locking on promo_code
-- ---------------------------------------------------------------
ALTER TABLE promo_code ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- ---------------------------------------------------------------
-- CRIT-03: employe_disponibilites — cascade on parent employe delete
-- ---------------------------------------------------------------
ALTER TABLE employe_disponibilites
    DROP CONSTRAINT IF EXISTS fk_disp_employe;
ALTER TABLE employe_disponibilites
    ADD CONSTRAINT fk_disp_employe
    FOREIGN KEY (employe_id) REFERENCES employe(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------
-- CRIT-03: employe_sites — cascade on parent employe delete
-- ---------------------------------------------------------------
ALTER TABLE employe_sites
    DROP CONSTRAINT IF EXISTS fk_empsite_employe;
ALTER TABLE employe_sites
    ADD CONSTRAINT fk_empsite_employe
    FOREIGN KEY (employe_id) REFERENCES employe(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------
-- CRIT-03: exigence_jours — cascade on parent exigence delete
--          Constraint name in V1 is fk_jour_exigence
-- ---------------------------------------------------------------
ALTER TABLE exigence_jours
    DROP CONSTRAINT IF EXISTS fk_jour_exigence;
ALTER TABLE exigence_jours
    ADD CONSTRAINT fk_jour_exigence
    FOREIGN KEY (exigence_id) REFERENCES exigence(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------
-- CRIT-03: parametres_jours_actifs — cascade on parent parametres delete
--          Constraint name in V1 is fk_joursactifs_parametres
-- ---------------------------------------------------------------
ALTER TABLE parametres_jours_actifs
    DROP CONSTRAINT IF EXISTS fk_joursactifs_parametres;
ALTER TABLE parametres_jours_actifs
    ADD CONSTRAINT fk_joursactifs_parametres
    FOREIGN KEY (parametres_id) REFERENCES parametres(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------
-- CRIT-03: parametres_regles_affectation — cascade on parent parametres delete
--          Constraint name in V1 is fk_regles_parametres
-- ---------------------------------------------------------------
ALTER TABLE parametres_regles_affectation
    DROP CONSTRAINT IF EXISTS fk_regles_parametres;
ALTER TABLE parametres_regles_affectation
    ADD CONSTRAINT fk_regles_parametres
    FOREIGN KEY (parametres_id) REFERENCES parametres(id) ON DELETE CASCADE;

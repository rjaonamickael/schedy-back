-- ============================================================
-- V34 : Refonte UX Gestion des Conges
-- ============================================================
-- OBJECTIF :
--   Remplacer 3 concepts flous (modeQuota, quotaIllimite, categorie string + 2 champs zombies
--   report_max/report_duree) par 3 concepts clairs aligned avec la realite metier :
--     - paye          : BOOLEAN (etait un enum string 'paye'/'non_paye')
--     - type_limite   : ENUM { ENVELOPPE_ANNUELLE | ACCRUAL | AUCUNE } (fusionne 3 flags)
--     - validite      : 2 dates nullables (periode specifique d'application)
--
-- STRATEGIE :
--   Migration preservatrice. Les donnees existantes sont mappees proprement :
--     - categorie='paye'               -> paye=true
--     - categorie='non_paye'           -> paye=false
--     - quota_illimite=true            -> type_limite='AUCUNE'
--     - accrual_montant IS NOT NULL    -> type_limite='ACCRUAL'
--     - sinon                          -> type_limite='ENVELOPPE_ANNUELLE'
--     - autoriser_negatif              -> autoriser_depassement (rename)
--
--   Les colonnes supprimees (mode_quota, report_max, report_duree, categorie,
--   quota_illimite) n'ont jamais ete exploitees dans la logique metier.
--
-- BONUS : dateRenouvellementConges au niveau Organisation (MM-DD, default 01-01).
--         Utilisee par RenouvellementCongesScheduler pour reset les banques annuelles.
-- ============================================================

-- ------------------------------------------------------------
-- 1. TYPE_CONGE : nouvelles colonnes
-- ------------------------------------------------------------
ALTER TABLE type_conge
    ADD COLUMN IF NOT EXISTS paye BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE type_conge
    ADD COLUMN IF NOT EXISTS type_limite VARCHAR(32) NOT NULL DEFAULT 'ENVELOPPE_ANNUELLE';

ALTER TABLE type_conge
    ADD COLUMN IF NOT EXISTS quota_annuel FLOAT8;

ALTER TABLE type_conge
    ADD COLUMN IF NOT EXISTS date_debut_validite DATE;

ALTER TABLE type_conge
    ADD COLUMN IF NOT EXISTS date_fin_validite DATE;

-- ------------------------------------------------------------
-- 2. TYPE_CONGE : migration des donnees existantes
-- ------------------------------------------------------------
-- Conversion categorie (enum string) -> paye (boolean)
UPDATE type_conge SET paye = TRUE  WHERE categorie = 'paye';
UPDATE type_conge SET paye = FALSE WHERE categorie = 'non_paye';

-- Derivation du type_limite a partir des anciens flags
UPDATE type_conge
SET type_limite = CASE
    WHEN quota_illimite = TRUE                THEN 'AUCUNE'
    WHEN accrual_montant IS NOT NULL          THEN 'ACCRUAL'
    ELSE                                           'ENVELOPPE_ANNUELLE'
END;

-- ------------------------------------------------------------
-- 3. TYPE_CONGE : rename autoriser_negatif -> autoriser_depassement
-- ------------------------------------------------------------
ALTER TABLE type_conge RENAME COLUMN autoriser_negatif TO autoriser_depassement;

-- ------------------------------------------------------------
-- 4. TYPE_CONGE : drop des colonnes obsoletes
-- ------------------------------------------------------------
ALTER TABLE type_conge DROP COLUMN IF EXISTS mode_quota;
ALTER TABLE type_conge DROP COLUMN IF EXISTS report_max;
ALTER TABLE type_conge DROP COLUMN IF EXISTS report_duree;
ALTER TABLE type_conge DROP COLUMN IF EXISTS quota_illimite;
ALTER TABLE type_conge DROP COLUMN IF EXISTS categorie;

-- ------------------------------------------------------------
-- 5. ORGANISATION : date de renouvellement des conges annuels
-- ------------------------------------------------------------
-- Format MM-DD (ex: '01-01' pour le 1er janvier, '04-06' pour le 6 avril fiscal UK).
-- Lue chaque jour a 2h du matin par RenouvellementCongesScheduler.
-- Default '01-01' couvre les cas les plus courants (Canada, France, Madagascar).
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS date_renouvellement_conges VARCHAR(5) NOT NULL DEFAULT '01-01';

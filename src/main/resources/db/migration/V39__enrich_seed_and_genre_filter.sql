-- V39 : two complementary changes shipped together :
--
--   1. Schema  : add a per-type-conge gender filter via a collection table
--                (type_conge_genres_eligibles). Empty/no row = open to all.
--                Used by CongeService.provisionBanquesForType /
--                provisionBanquesForEmploye to decide if an employe is
--                eligible (e.g. maternite restricted to FEMME).
--
--   2. Seed    : enrich the V2 demo employees with genre / numero_employe /
--                date_naissance ; turn on tiered pause rules on the existing
--                parametres rows ; create Maternite (FEMME only) and
--                Paternite (HOMME + AUTRE) types and provision matching
--                banques for the eligible employees.
--
-- Re-run safe : every UPDATE/INSERT is guarded so the migration is a no-op
-- on a fresh db without seed data, and idempotent if accidentally replayed.

-- ============================================================
-- 1. Collection table : type_conge → genres eligibles
-- ============================================================
CREATE TABLE IF NOT EXISTS type_conge_genres_eligibles (
    type_conge_id VARCHAR(255) NOT NULL,
    genre VARCHAR(16) NOT NULL,
    PRIMARY KEY (type_conge_id, genre),
    CONSTRAINT fk_tcge_type FOREIGN KEY (type_conge_id) REFERENCES type_conge(id) ON DELETE CASCADE,
    CONSTRAINT chk_tcge_genre CHECK (genre IN ('HOMME', 'FEMME', 'AUTRE'))
);

CREATE INDEX IF NOT EXISTS idx_tcge_type ON type_conge_genres_eligibles (type_conge_id);

-- ============================================================
-- 2. Seed enrichment (employees + parametres + new types)
-- ============================================================
DO $$
DECLARE
    v_org_ent_id  TEXT;
    v_org_comp_id TEXT;
    v_tc_mat_ent  TEXT := gen_random_uuid()::TEXT;
    v_tc_pat_ent  TEXT := gen_random_uuid()::TEXT;
    v_tc_mat_comp TEXT := gen_random_uuid()::TEXT;
    v_tc_pat_comp TEXT := gen_random_uuid()::TEXT;
    v_today       DATE := CURRENT_DATE;
BEGIN
    SELECT id INTO v_org_ent_id  FROM organisation WHERE nom = 'Entreprise' LIMIT 1;
    SELECT id INTO v_org_comp_id FROM organisation WHERE nom = 'Company'    LIMIT 1;

    IF v_org_ent_id IS NULL OR v_org_comp_id IS NULL THEN
        RAISE NOTICE 'V39 : seed orgs not found, skipping seed enrichment';
        RETURN;
    END IF;

    -- ============================================================
    -- 2a. Backfill genre / numero_employe / date_naissance on the
    --     V2 seed employees. Names are mapped 1:1 ; rows with no V2
    --     match are left untouched.
    -- ============================================================
    -- Entreprise (Madagascar)
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPMG001', date_naissance = '1985-04-12' WHERE id = 'ent-1';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'EMPMG002', date_naissance = '1990-08-23' WHERE id = 'ent-2';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPMG003', date_naissance = '1978-11-02' WHERE id = 'ent-3';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'EMPMG004', date_naissance = '1995-02-17' WHERE id = 'ent-4';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPMG005', date_naissance = '1982-07-09' WHERE id = 'ent-5';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'MGRMG001', date_naissance = '1976-03-30' WHERE id = 'mgr-ent-1';

    -- Company (Quebec)
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPQC001', date_naissance = '1988-06-14' WHERE id = 'comp-1';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'EMPQC002', date_naissance = '1992-10-05' WHERE id = 'comp-2';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'EMPQC003', date_naissance = '1980-01-22' WHERE id = 'comp-3';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPQC004', date_naissance = '1975-09-18' WHERE id = 'comp-4';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPQC005', date_naissance = '1996-12-03' WHERE id = 'comp-5';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'EMPQC006', date_naissance = '1993-05-27' WHERE id = 'comp-6';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'EMPQC007', date_naissance = '1987-08-11' WHERE id = 'comp-7';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPQC008', date_naissance = '1983-04-29' WHERE id = 'comp-8';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPQC009', date_naissance = '1997-11-15' WHERE id = 'comp-9';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'EMPQC010', date_naissance = '1991-02-08' WHERE id = 'comp-10';
    UPDATE employe SET genre = 'AUTRE', numero_employe = 'EMPQC011', date_naissance = '1999-07-21' WHERE id = 'comp-11';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'EMPQC012', date_naissance = '1979-10-04' WHERE id = 'comp-12';
    UPDATE employe SET genre = 'HOMME', numero_employe = 'MGRQC001', date_naissance = '1974-05-19' WHERE id = 'mgr-comp-1';
    UPDATE employe SET genre = 'FEMME', numero_employe = 'MGRQC002', date_naissance = '1981-12-30' WHERE id = 'mgr-comp-2';

    -- ============================================================
    -- 2b. Pause rules : turn on advanced mode on every existing
    --     parametres row in both demo orgs, and seed two tiers
    --     (≥6h → 1 short PAUSE 15min payee ; ≥8h → 1 REPAS 30min unpaid
    --     + 1 short PAUSE 15min payee).
    -- ============================================================
    UPDATE parametres
       SET pause_avancee = TRUE,
           fenetre_pause_min_minutes = 15,
           fenetre_pause_max_minutes = 90
     WHERE organisation_id IN (v_org_ent_id, v_org_comp_id);

    -- Insert the tiered rules only when none exist yet for the row.
    INSERT INTO parametres_regles_pause (parametres_id, seuil_min_heures, seuil_max_heures, type, duree_minutes, payee, divisible, fraction_min_minutes, fenetre_debut, fenetre_fin, ordre)
    SELECT p.id, 6.0, 8.0, 'PAUSE', 15, TRUE, FALSE, NULL, NULL, NULL, 0
      FROM parametres p
     WHERE p.organisation_id IN (v_org_ent_id, v_org_comp_id)
       AND NOT EXISTS (SELECT 1 FROM parametres_regles_pause r WHERE r.parametres_id = p.id);

    INSERT INTO parametres_regles_pause (parametres_id, seuil_min_heures, seuil_max_heures, type, duree_minutes, payee, divisible, fraction_min_minutes, fenetre_debut, fenetre_fin, ordre)
    SELECT p.id, 8.0, NULL, 'REPAS', 30, FALSE, FALSE, NULL, NULL, NULL, 1
      FROM parametres p
     WHERE p.organisation_id IN (v_org_ent_id, v_org_comp_id)
       AND NOT EXISTS (
           SELECT 1 FROM parametres_regles_pause r
            WHERE r.parametres_id = p.id AND r.seuil_min_heures = 8.0
       );

    INSERT INTO parametres_regles_pause (parametres_id, seuil_min_heures, seuil_max_heures, type, duree_minutes, payee, divisible, fraction_min_minutes, fenetre_debut, fenetre_fin, ordre)
    SELECT p.id, 8.0, NULL, 'PAUSE', 15, TRUE, FALSE, NULL, NULL, NULL, 2
      FROM parametres p
     WHERE p.organisation_id IN (v_org_ent_id, v_org_comp_id)
       AND EXISTS (
           SELECT 1 FROM parametres_regles_pause r
            WHERE r.parametres_id = p.id AND r.seuil_min_heures = 8.0 AND r.type = 'REPAS'
       )
       AND NOT EXISTS (
           SELECT 1 FROM parametres_regles_pause r
            WHERE r.parametres_id = p.id AND r.seuil_min_heures = 8.0 AND r.type = 'PAUSE'
       );

    -- ============================================================
    -- 2c. Maternite + Paternite types restricted by genre
    --     - Maternite : FEMME only, 16 weeks (112 days)
    --     - Paternite : HOMME + AUTRE, 5 weeks (35 days)
    --     Both ENVELOPPE_ANNUELLE, paye, no overflow.
    -- ============================================================
    -- Entreprise — Maternite
    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel, accrual_montant, accrual_frequence,
        autoriser_depassement, date_debut_validite, date_fin_validite, organisation_id
    )
    SELECT v_tc_mat_ent, 'Conge maternite', TRUE, 'jours', '#EC4899',
           'ENVELOPPE_ANNUELLE', 112, NULL, NULL,
           FALSE, NULL, NULL, v_org_ent_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge WHERE organisation_id = v_org_ent_id AND nom = 'Conge maternite'
    );
    INSERT INTO type_conge_genres_eligibles (type_conge_id, genre)
    SELECT id, 'FEMME' FROM type_conge
     WHERE organisation_id = v_org_ent_id AND nom = 'Conge maternite'
       AND NOT EXISTS (
           SELECT 1 FROM type_conge_genres_eligibles g
            WHERE g.type_conge_id = type_conge.id AND g.genre = 'FEMME'
       );

    -- Entreprise — Paternite
    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel, accrual_montant, accrual_frequence,
        autoriser_depassement, date_debut_validite, date_fin_validite, organisation_id
    )
    SELECT v_tc_pat_ent, 'Conge paternite', TRUE, 'jours', '#3B82F6',
           'ENVELOPPE_ANNUELLE', 35, NULL, NULL,
           FALSE, NULL, NULL, v_org_ent_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge WHERE organisation_id = v_org_ent_id AND nom = 'Conge paternite'
    );
    INSERT INTO type_conge_genres_eligibles (type_conge_id, genre)
    SELECT id, g.g FROM type_conge
    CROSS JOIN (VALUES ('HOMME'), ('AUTRE')) AS g(g)
     WHERE organisation_id = v_org_ent_id AND nom = 'Conge paternite'
       AND NOT EXISTS (
           SELECT 1 FROM type_conge_genres_eligibles tge
            WHERE tge.type_conge_id = type_conge.id AND tge.genre = g.g
       );

    -- Company — Maternite
    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel, accrual_montant, accrual_frequence,
        autoriser_depassement, date_debut_validite, date_fin_validite, organisation_id
    )
    SELECT v_tc_mat_comp, 'Conge maternite', TRUE, 'jours', '#EC4899',
           'ENVELOPPE_ANNUELLE', 112, NULL, NULL,
           FALSE, NULL, NULL, v_org_comp_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge WHERE organisation_id = v_org_comp_id AND nom = 'Conge maternite'
    );
    INSERT INTO type_conge_genres_eligibles (type_conge_id, genre)
    SELECT id, 'FEMME' FROM type_conge
     WHERE organisation_id = v_org_comp_id AND nom = 'Conge maternite'
       AND NOT EXISTS (
           SELECT 1 FROM type_conge_genres_eligibles g
            WHERE g.type_conge_id = type_conge.id AND g.genre = 'FEMME'
       );

    -- Company — Paternite
    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel, accrual_montant, accrual_frequence,
        autoriser_depassement, date_debut_validite, date_fin_validite, organisation_id
    )
    SELECT v_tc_pat_comp, 'Conge paternite', TRUE, 'jours', '#3B82F6',
           'ENVELOPPE_ANNUELLE', 35, NULL, NULL,
           FALSE, NULL, NULL, v_org_comp_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge WHERE organisation_id = v_org_comp_id AND nom = 'Conge paternite'
    );
    INSERT INTO type_conge_genres_eligibles (type_conge_id, genre)
    SELECT id, g.g FROM type_conge
    CROSS JOIN (VALUES ('HOMME'), ('AUTRE')) AS g(g)
     WHERE organisation_id = v_org_comp_id AND nom = 'Conge paternite'
       AND NOT EXISTS (
           SELECT 1 FROM type_conge_genres_eligibles tge
            WHERE tge.type_conge_id = type_conge.id AND tge.genre = g.g
       );

    -- ============================================================
    -- 2d. Provision banques for the new restricted types : create a
    --     banque only for employees whose genre is in the type's
    --     eligibility set. Idempotent : skips employees that already
    --     have a banque for that type.
    -- ============================================================
    INSERT INTO banque_conge (
        id, employe_id, type_conge_id, quota, utilise, en_attente,
        date_debut, date_fin, organisation_id, version
    )
    SELECT
        gen_random_uuid()::TEXT,
        e.id,
        t.id,
        COALESCE(t.quota_annuel, 0),
        0, 0,
        v_today,
        v_today + INTERVAL '1 year',
        e.organisation_id,
        0
    FROM employe e
    INNER JOIN type_conge t ON t.organisation_id = e.organisation_id
    INNER JOIN type_conge_genres_eligibles g ON g.type_conge_id = t.id AND g.genre = e.genre
    WHERE e.organisation_id IN (v_org_ent_id, v_org_comp_id)
      AND t.nom IN ('Conge maternite', 'Conge paternite')
      AND e.genre IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM banque_conge b
           WHERE b.employe_id     = e.id
             AND b.type_conge_id  = t.id
             AND b.organisation_id = e.organisation_id
      );

END $$;

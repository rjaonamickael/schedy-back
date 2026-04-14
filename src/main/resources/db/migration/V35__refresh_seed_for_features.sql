-- ============================================================
-- V35__refresh_seed_for_features.sql
--
-- Refreshes the V2 seed data of TypeConge + BanqueConge to showcase
-- ALL leave management features implemented through V34 :
--
--   1. Sets quota_annuel on existing ENVELOPPE_ANNUELLE types (CP, Maternité)
--      so the type list shows the actual default instead of "—".
--   2. Adds 2 new showcase types per organisation :
--        - ACCRUAL type ("Vacances Québec" : Loi sur les normes du travail = 2.5j/mois)
--        - Validity-bounded type ("Congé Fêtes" : 15 déc → 5 jan only)
--   3. Auto-provisions BanqueConge for every (employe, type) pair so the
--      invariant from CongeService.provisionBanques* is reflected in the seed
--      (every employee has access to every type, with type-default quotas).
--   4. Sets dateRenouvellementConges on each organisation (Canada/Madagascar default).
--
-- Idempotent : guarded by EXISTS checks so it can be re-run without duplicating.
-- Safe for first-run on fresh DB (V2 + V34 already created the base schema/data).
-- ============================================================

DO $$
DECLARE
    v_org_ent_id  TEXT;
    v_org_comp_id TEXT;

    v_tc_cp_ent   TEXT; v_tc_mat_ent  TEXT;
    v_tc_cp_comp  TEXT; v_tc_mat_comp TEXT;

    v_tc_accrual_ent  TEXT := gen_random_uuid()::TEXT;
    v_tc_accrual_comp TEXT := gen_random_uuid()::TEXT;
    v_tc_fetes_ent    TEXT := gen_random_uuid()::TEXT;
    v_tc_fetes_comp   TEXT := gen_random_uuid()::TEXT;

    v_today DATE := CURRENT_DATE;
BEGIN
    -- --------------------------------------------------------
    -- Locate the seeded organisations (skip if missing)
    -- --------------------------------------------------------
    SELECT id INTO v_org_ent_id  FROM organisation WHERE nom = 'Entreprise' LIMIT 1;
    SELECT id INTO v_org_comp_id FROM organisation WHERE nom = 'Company'    LIMIT 1;

    IF v_org_ent_id IS NULL OR v_org_comp_id IS NULL THEN
        RAISE NOTICE 'V35 : seed orgs not found, skipping enrichment';
        RETURN;
    END IF;

    -- --------------------------------------------------------
    -- 1. Default renewal day per org (Canada + Madagascar : 1er janvier)
    -- --------------------------------------------------------
    UPDATE organisation
       SET date_renouvellement_conges = '01-01'
     WHERE id IN (v_org_ent_id, v_org_comp_id);

    -- --------------------------------------------------------
    -- 2. Set quota_annuel on existing ENVELOPPE_ANNUELLE types (CP + Maternité)
    --    Without this, the type list shows "—" instead of "20 jours par an".
    -- --------------------------------------------------------
    UPDATE type_conge
       SET quota_annuel = 20
     WHERE organisation_id IN (v_org_ent_id, v_org_comp_id)
       AND nom = 'Congé payé'
       AND quota_annuel IS NULL;

    UPDATE type_conge
       SET quota_annuel = 112       -- 16 weeks at 7 days
     WHERE organisation_id IN (v_org_ent_id, v_org_comp_id)
       AND nom = 'Congé maternité'
       AND quota_annuel IS NULL;

    -- --------------------------------------------------------
    -- 3. ACCRUAL showcase : "Vacances accumulées" — Quebec-style 2.5j/mois
    -- --------------------------------------------------------
    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel,
        accrual_montant, accrual_frequence,
        autoriser_depassement,
        date_debut_validite, date_fin_validite,
        organisation_id
    )
    SELECT v_tc_accrual_ent, 'Vacances accumulées', TRUE, 'jours', '#06B6D4',
           'ACCRUAL', NULL, 2.5, 'mensuel', FALSE, NULL, NULL, v_org_ent_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge
         WHERE organisation_id = v_org_ent_id AND nom = 'Vacances accumulées'
    );

    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel,
        accrual_montant, accrual_frequence,
        autoriser_depassement,
        date_debut_validite, date_fin_validite,
        organisation_id
    )
    SELECT v_tc_accrual_comp, 'Vacances accumulées', TRUE, 'jours', '#06B6D4',
           'ACCRUAL', NULL, 2.5, 'mensuel', FALSE, NULL, NULL, v_org_comp_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge
         WHERE organisation_id = v_org_comp_id AND nom = 'Vacances accumulées'
    );

    -- --------------------------------------------------------
    -- 4. Validity-bounded showcase : "Congé Fêtes" — 15 déc → 5 jan only
    --    Demonstrates the dateDebutValidite / dateFinValidite feature.
    -- --------------------------------------------------------
    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel,
        accrual_montant, accrual_frequence,
        autoriser_depassement,
        date_debut_validite, date_fin_validite,
        organisation_id
    )
    SELECT v_tc_fetes_ent, 'Congé Fêtes', TRUE, 'jours', '#A855F7',
           'ENVELOPPE_ANNUELLE', 3, NULL, NULL, FALSE,
           DATE (extract(year from v_today)::TEXT || '-12-15'),
           DATE ((extract(year from v_today)::INT + 1)::TEXT || '-01-05'),
           v_org_ent_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge
         WHERE organisation_id = v_org_ent_id AND nom = 'Congé Fêtes'
    );

    INSERT INTO type_conge (
        id, nom, paye, unite, couleur,
        type_limite, quota_annuel,
        accrual_montant, accrual_frequence,
        autoriser_depassement,
        date_debut_validite, date_fin_validite,
        organisation_id
    )
    SELECT v_tc_fetes_comp, 'Congé Fêtes', TRUE, 'jours', '#A855F7',
           'ENVELOPPE_ANNUELLE', 3, NULL, NULL, FALSE,
           DATE (extract(year from v_today)::TEXT || '-12-15'),
           DATE ((extract(year from v_today)::INT + 1)::TEXT || '-01-05'),
           v_org_comp_id
    WHERE NOT EXISTS (
        SELECT 1 FROM type_conge
         WHERE organisation_id = v_org_comp_id AND nom = 'Congé Fêtes'
    );

    -- --------------------------------------------------------
    -- 5. Auto-provision : every (employe, type) pair gets a banque.
    --    Mimics CongeService.provisionBanquesForType / provisionBanquesForEmploye.
    --    Quota strategy :
    --      - ENVELOPPE_ANNUELLE → use type.quota_annuel as default
    --      - ACCRUAL            → start at 0 (scheduler will credit)
    --      - AUCUNE             → null (= unlimited, displays as ∞)
    -- --------------------------------------------------------
    INSERT INTO banque_conge (
        id, employe_id, type_conge_id,
        quota, utilise, en_attente,
        date_debut, date_fin,
        organisation_id, version
    )
    SELECT
        gen_random_uuid()::TEXT,
        e.id,
        t.id,
        CASE
            WHEN t.type_limite = 'AUCUNE'             THEN NULL
            WHEN t.type_limite = 'ACCRUAL'            THEN 0
            WHEN t.type_limite = 'ENVELOPPE_ANNUELLE' THEN COALESCE(t.quota_annuel, 0)
        END,
        0, 0,
        v_today,
        v_today + INTERVAL '1 year',
        e.organisation_id,
        0
    FROM employe e
    INNER JOIN type_conge t ON t.organisation_id = e.organisation_id
    WHERE e.organisation_id IN (v_org_ent_id, v_org_comp_id)
      AND NOT EXISTS (
          SELECT 1 FROM banque_conge b
           WHERE b.employe_id     = e.id
             AND b.type_conge_id  = t.id
             AND b.organisation_id = e.organisation_id
      );

    RAISE NOTICE 'V35 : seed enriched for leave management features';
    RAISE NOTICE '  - quota_annuel set on existing CP/Maternité types';
    RAISE NOTICE '  - 2 ACCRUAL types added (Vacances accumulées, 2.5j/mois)';
    RAISE NOTICE '  - 2 validity-bounded types added (Congé Fêtes, 15 déc → 5 jan)';
    RAISE NOTICE '  - banques auto-provisioned for every (employe, type) pair';
    RAISE NOTICE '  - dateRenouvellementConges = 01-01 set on both orgs';
END $$;

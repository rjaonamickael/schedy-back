-- ============================================================
-- V29 — Update seed roles with professional colors and icons
-- ============================================================
-- Applies to the 8 retail roles of the 2 seed organizations
-- (Entreprise — Madagascar, Company — Québec).
--
-- Color palette: Tailwind-inspired, professional and differentiated.
-- Icons: from the 366-icon library (src/app/icons.ts), kebab-case keys.
--
-- Idempotent: only updates rows that match the seed organizations by
-- joining on the organisation name. If the seed orgs don't exist
-- (fresh beta prod), this migration is a no-op.
-- ============================================================

DO $$
DECLARE
    v_org_ent_id  TEXT;
    v_org_comp_id TEXT;
BEGIN
    SELECT id INTO v_org_ent_id  FROM organisation WHERE nom = 'Entreprise' LIMIT 1;
    SELECT id INTO v_org_comp_id FROM organisation WHERE nom = 'Company'    LIMIT 1;

    -- If neither seed org exists, skip entirely
    IF v_org_ent_id IS NULL AND v_org_comp_id IS NULL THEN
        RAISE NOTICE 'V29: No seed organizations found, skipping role update';
        RETURN;
    END IF;

    -- Update Entreprise (Madagascar) roles
    IF v_org_ent_id IS NOT NULL THEN
        UPDATE role SET couleur = '#0EA5E9', icone = 'cash-register'
            WHERE organisation_id = v_org_ent_id AND nom = 'Caissier';
        UPDATE role SET couleur = '#10B981', icone = 'store'
            WHERE organisation_id = v_org_ent_id AND nom = 'Vendeur';
        UPDATE role SET couleur = '#8B5CF6', icone = 'clipboard-list'
            WHERE organisation_id = v_org_ent_id AND nom = 'Responsable rayon';
        UPDATE role SET couleur = '#DC2626', icone = 'shield-check'
            WHERE organisation_id = v_org_ent_id AND nom = 'Agent de sécurité';
        UPDATE role SET couleur = '#F59E0B', icone = 'forklift'
            WHERE organisation_id = v_org_ent_id AND nom = 'Manutentionnaire';
        UPDATE role SET couleur = '#EC4899', icone = 'headset'
            WHERE organisation_id = v_org_ent_id AND nom = 'Accueil';
        UPDATE role SET couleur = '#6366F1', icone = 'cash-banknote'
            WHERE organisation_id = v_org_ent_id AND nom = 'Chef de caisse';
        UPDATE role SET couleur = '#64748B', icone = 'barcode'
            WHERE organisation_id = v_org_ent_id AND nom = 'Magasinier';
    END IF;

    -- Update Company (Québec) roles — same palette + icons
    IF v_org_comp_id IS NOT NULL THEN
        UPDATE role SET couleur = '#0EA5E9', icone = 'cash-register'
            WHERE organisation_id = v_org_comp_id AND nom = 'Caissier';
        UPDATE role SET couleur = '#10B981', icone = 'store'
            WHERE organisation_id = v_org_comp_id AND nom = 'Vendeur';
        UPDATE role SET couleur = '#8B5CF6', icone = 'clipboard-list'
            WHERE organisation_id = v_org_comp_id AND nom = 'Responsable rayon';
        UPDATE role SET couleur = '#DC2626', icone = 'shield-check'
            WHERE organisation_id = v_org_comp_id AND nom = 'Agent de sécurité';
        UPDATE role SET couleur = '#F59E0B', icone = 'forklift'
            WHERE organisation_id = v_org_comp_id AND nom = 'Manutentionnaire';
        UPDATE role SET couleur = '#EC4899', icone = 'headset'
            WHERE organisation_id = v_org_comp_id AND nom = 'Accueil';
        UPDATE role SET couleur = '#6366F1', icone = 'cash-banknote'
            WHERE organisation_id = v_org_comp_id AND nom = 'Chef de caisse';
        UPDATE role SET couleur = '#64748B', icone = 'barcode'
            WHERE organisation_id = v_org_comp_id AND nom = 'Magasinier';
    END IF;
END $$;

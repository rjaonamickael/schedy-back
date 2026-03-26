-- ============================================================
-- V2__seed_data.sql  —  Schedy DEV seed data
-- Replaces DataInitializer.java and the old H2 data.sql.
--
-- Requires: pgcrypto extension (created in V1).
-- Guard: skips entirely if any organisation already exists.
--
-- Test accounts:
--
--   COMPANY (3 sites):
--     admin@company.com           / admin123    (ADMIN)
--     manager.centre@company.com  / manager123  (MANAGER)
--     manager.sud@company.com     / manager123  (MANAGER)
--     luc.raharison@company.com   / employe123  (EMPLOYEE)
--     ... all employees: employe123
--
--   ENTREPRISE (1 site):
--     admin@entreprise.com        / admin123    (ADMIN)
--     manager@entreprise.com      / manager123  (MANAGER)
--     jean.rakoto@entreprise.com  / employe123  (EMPLOYEE)
--     ... all employees: employe123
--
--   SUPERADMIN:
--     superadmin@schedy.io / ChangeMeOnFirstLogin1!
--     (set SUPERADMIN_PASSWORD env var in prod — CHANGE IMMEDIATELY)
--
-- PIN convention (employes):
--   pin       = BCrypt hash via pgcrypto crypt()
--   pin_hash  = SHA-256 hex via pgcrypto digest()
--   pin_clair = raw PIN text
-- ============================================================

DO $$
DECLARE
    v_org_ent_id  TEXT := gen_random_uuid()::TEXT;
    v_org_comp_id TEXT := gen_random_uuid()::TEXT;

    v_site_siege  TEXT := gen_random_uuid()::TEXT;
    v_site_cv     TEXT := gen_random_uuid()::TEXT;
    v_site_bs     TEXT := gen_random_uuid()::TEXT;
    v_site_zi     TEXT := gen_random_uuid()::TEXT;

    -- Type conge IDs — entreprise
    v_tc_cp_ent   TEXT := gen_random_uuid()::TEXT;
    v_tc_css_ent  TEXT := gen_random_uuid()::TEXT;
    v_tc_am_ent   TEXT := gen_random_uuid()::TEXT;
    v_tc_mat_ent  TEXT := gen_random_uuid()::TEXT;

    -- Type conge IDs — company
    v_tc_cp_comp  TEXT := gen_random_uuid()::TEXT;
    v_tc_css_comp TEXT := gen_random_uuid()::TEXT;
    v_tc_am_comp  TEXT := gen_random_uuid()::TEXT;
    v_tc_mat_comp TEXT := gen_random_uuid()::TEXT;

    -- Current week in ISO format (e.g. 2026-W13)
    v_semaine TEXT;
    v_today   DATE := CURRENT_DATE;

    -- Pre-computed BCrypt hashes (cost 10, stable values for seeding)
    -- admin123
    h_admin    TEXT := crypt('admin123',   gen_salt('bf', 10));
    -- manager123
    h_manager  TEXT := crypt('manager123', gen_salt('bf', 10));
    -- employe123
    h_employe  TEXT := crypt('employe123', gen_salt('bf', 10));
    -- superadmin default password
    h_superadmin TEXT := crypt('ChangeMeOnFirstLogin1!', gen_salt('bf', 12));

    -- Employee PIN hashes (BCrypt)
    pin_1111_bcrypt TEXT := crypt('1234', gen_salt('bf', 10));
    pin_5678_bcrypt TEXT := crypt('5678', gen_salt('bf', 10));
    pin_9012_bcrypt TEXT := crypt('9012', gen_salt('bf', 10));
    pin_3456_bcrypt TEXT := crypt('3456', gen_salt('bf', 10));
    pin_7890_bcrypt TEXT := crypt('7890', gen_salt('bf', 10));
    pin_1111_bcrypt2 TEXT := crypt('1111', gen_salt('bf', 10));
    pin_2222_bcrypt TEXT := crypt('2222', gen_salt('bf', 10));
    pin_3333_bcrypt TEXT := crypt('3333', gen_salt('bf', 10));
    pin_4444_bcrypt TEXT := crypt('4444', gen_salt('bf', 10));
    pin_5555_bcrypt TEXT := crypt('5555', gen_salt('bf', 10));
    pin_6666_bcrypt TEXT := crypt('6666', gen_salt('bf', 10));
    pin_7777_bcrypt TEXT := crypt('7777', gen_salt('bf', 10));
    pin_8888_bcrypt TEXT := crypt('8888', gen_salt('bf', 10));
    pin_9999_bcrypt TEXT := crypt('9999', gen_salt('bf', 10));
    pin_1010_bcrypt TEXT := crypt('1010', gen_salt('bf', 10));
    pin_1122_bcrypt TEXT := crypt('1122', gen_salt('bf', 10));
    pin_1133_bcrypt TEXT := crypt('1133', gen_salt('bf', 10));
    -- Manager PINs
    pin_mgr1_bcrypt TEXT := crypt('5000', gen_salt('bf', 10));
    pin_mgr2_bcrypt TEXT := crypt('6000', gen_salt('bf', 10));
    pin_mgr3_bcrypt TEXT := crypt('7000', gen_salt('bf', 10));

BEGIN
    -- --------------------------------------------------------
    -- Guard: skip if data already exists
    -- --------------------------------------------------------
    IF (SELECT COUNT(*) FROM organisation) > 0 THEN
        RAISE NOTICE 'Seed data already present — skipping V2__seed_data.sql';
        RETURN;
    END IF;

    -- Compute current ISO week string
    v_semaine := to_char(v_today, 'IYYY') || '-W' || lpad(to_char(v_today, 'IW'), 2, '0');

    -- ============================================================
    -- 1. ORGANISATIONS
    -- ============================================================
    INSERT INTO organisation (id, nom, domaine, status, created_at, pays) VALUES
        (v_org_ent_id,  'Entreprise', 'entreprise.com', 'ACTIVE', now(), 'MDG'),
        (v_org_comp_id, 'Company',    'company.com',    'ACTIVE', now(), 'CAN');

    -- ============================================================
    -- 2. SITES
    -- ============================================================
    -- Entreprise: 1 site (Madagascar)
    INSERT INTO site (id, nom, adresse, ville, code_postal, telephone, actif, organisation_id) VALUES
        (v_site_siege, 'Siège Entreprise', 'Antananarivo Centre', 'Antananarivo', '101', null, true, v_org_ent_id);

    -- Company: 3 sites (Québec)
    INSERT INTO site (id, nom, adresse, ville, code_postal, telephone, actif, organisation_id) VALUES
        (v_site_cv, 'Centre-Ville',     '275 Rue Saint-Jean',            'Québec',   'G1R 4S3', null, true, v_org_comp_id),
        (v_site_bs, 'Sainte-Foy',       '2450 Boulevard Laurier',        'Québec',   'G1V 4T3', null, true, v_org_comp_id),
        (v_site_zi, 'Parc Industriel',  '1200 Route de l''Église',       'Québec',   'G1N 4B5', null, true, v_org_comp_id);

    -- ============================================================
    -- 3. ROLES (8 per organisation)
    -- ============================================================
    -- Entreprise roles
    INSERT INTO role (id, nom, importance, couleur, organisation_id) VALUES
        (gen_random_uuid(), 'Caissier',           1, '#4CAF50', v_org_ent_id),
        (gen_random_uuid(), 'Vendeur',             2, '#2196F3', v_org_ent_id),
        (gen_random_uuid(), 'Responsable rayon',   3, '#FF9800', v_org_ent_id),
        (gen_random_uuid(), 'Agent de sécurité',   4, '#F44336', v_org_ent_id),
        (gen_random_uuid(), 'Manutentionnaire',    5, '#795548', v_org_ent_id),
        (gen_random_uuid(), 'Accueil',             6, '#9C27B0', v_org_ent_id),
        (gen_random_uuid(), 'Chef de caisse',      7, '#E91E63', v_org_ent_id),
        (gen_random_uuid(), 'Magasinier',          8, '#607D8B', v_org_ent_id);

    -- Company roles
    INSERT INTO role (id, nom, importance, couleur, organisation_id) VALUES
        (gen_random_uuid(), 'Caissier',           1, '#4CAF50', v_org_comp_id),
        (gen_random_uuid(), 'Vendeur',             2, '#2196F3', v_org_comp_id),
        (gen_random_uuid(), 'Responsable rayon',   3, '#FF9800', v_org_comp_id),
        (gen_random_uuid(), 'Agent de sécurité',   4, '#F44336', v_org_comp_id),
        (gen_random_uuid(), 'Manutentionnaire',    5, '#795548', v_org_comp_id),
        (gen_random_uuid(), 'Accueil',             6, '#9C27B0', v_org_comp_id),
        (gen_random_uuid(), 'Chef de caisse',      7, '#E91E63', v_org_comp_id),
        (gen_random_uuid(), 'Magasinier',          8, '#607D8B', v_org_comp_id);

    -- ============================================================
    -- 4. EMPLOYES — Entreprise (5 employees, fixed IDs)
    -- PIN format: pin=BCrypt, pin_hash=SHA-256 hex, pin_clair=raw
    -- ============================================================
    INSERT INTO employe (id, nom, role, email, telephone, date_embauche,
                         pin, pin_hash, pin_clair, organisation_id) VALUES
        ('ent-1', 'Rakoto Jean',    'Caissier',          'jean.rakoto@entreprise.com',    '+261341000001', '2024-01-15',
         pin_1111_bcrypt,  encode(digest('1234', 'sha256'), 'hex'), '1234', v_org_ent_id),
        ('ent-2', 'Rabe Marie',     'Vendeur',           'marie.rabe@entreprise.com',     '+261341000002', '2024-01-15',
         pin_5678_bcrypt,  encode(digest('5678', 'sha256'), 'hex'), '5678', v_org_ent_id),
        ('ent-3', 'Andria Paul',    'Responsable rayon', 'paul.andria@entreprise.com',    '+261341000003', '2024-01-15',
         pin_9012_bcrypt,  encode(digest('9012', 'sha256'), 'hex'), '9012', v_org_ent_id),
        ('ent-4', 'Rasoa Nadia',    'Caissier',          'nadia.rasoa@entreprise.com',    '+261341000004', '2024-01-15',
         pin_3456_bcrypt,  encode(digest('3456', 'sha256'), 'hex'), '3456', v_org_ent_id),
        ('ent-5', 'Randria Hery',   'Manutentionnaire',  'hery.randria@entreprise.com',   '+261341000005', '2024-01-15',
         pin_7890_bcrypt,  encode(digest('7890', 'sha256'), 'hex'), '7890', v_org_ent_id);

    -- Disponibilités Entreprise (Lundi-Vendredi, heures selon DataInitializer)
    INSERT INTO employe_disponibilites (employe_id, jour, heure_debut, heure_fin) VALUES
        ('ent-1', 1, 8, 17), ('ent-1', 2, 8, 17), ('ent-1', 3, 8, 17), ('ent-1', 4, 8, 17), ('ent-1', 5, 8, 17),
        ('ent-2', 1, 9, 18), ('ent-2', 2, 9, 18), ('ent-2', 3, 9, 18), ('ent-2', 4, 9, 18), ('ent-2', 5, 9, 18),
        ('ent-3', 1, 8, 17), ('ent-3', 2, 8, 17), ('ent-3', 3, 8, 17), ('ent-3', 4, 8, 17), ('ent-3', 5, 8, 17),
        ('ent-4', 1, 8, 17), ('ent-4', 2, 8, 17), ('ent-4', 3, 8, 17), ('ent-4', 4, 8, 17), ('ent-4', 5, 8, 17),
        ('ent-5', 1, 6, 14), ('ent-5', 2, 6, 14), ('ent-5', 3, 6, 14), ('ent-5', 4, 6, 14), ('ent-5', 5, 6, 14);

    -- Site assignments Entreprise (tous sur siège)
    INSERT INTO employe_sites (employe_id, site_id) VALUES
        ('ent-1', v_site_siege),
        ('ent-2', v_site_siege),
        ('ent-3', v_site_siege),
        ('ent-4', v_site_siege),
        ('ent-5', v_site_siege);

    -- ============================================================
    -- 5. EMPLOYES — Company (12 employees, fixed IDs)
    -- ============================================================
    INSERT INTO employe (id, nom, role, email, telephone, date_embauche,
                         pin, pin_hash, pin_clair, organisation_id) VALUES
        -- Centre-Ville (Québec)
        ('comp-1',  'Marc Tremblay',            'Caissier',          'marc.tremblay@company.com',       '+15145551001', '2024-01-15',
         pin_1111_bcrypt2, encode(digest('1111', 'sha256'), 'hex'), '1111', v_org_comp_id),
        ('comp-2',  'Sophie Gagnon',            'Vendeur',           'sophie.gagnon@company.com',        '+15145551002', '2024-01-15',
         pin_2222_bcrypt,  encode(digest('2222', 'sha256'), 'hex'), '2222', v_org_comp_id),
        ('comp-3',  'Isabelle Roy',             'Chef de caisse',    'isabelle.roy@company.com',         '+15145551003', '2024-01-15',
         pin_3333_bcrypt,  encode(digest('3333', 'sha256'), 'hex'), '3333', v_org_comp_id),
        ('comp-4',  'Pierre Bouchard',          'Agent de sécurité', 'pierre.bouchard@company.com',     '+15145551004', '2024-01-15',
         pin_4444_bcrypt,  encode(digest('4444', 'sha256'), 'hex'), '4444', v_org_comp_id),
        -- Sainte-Foy
        ('comp-5',  'Alexandre Côté',           'Vendeur',           'alexandre.cote@company.com',       '+15145551005', '2024-01-15',
         pin_5555_bcrypt,  encode(digest('5555', 'sha256'), 'hex'), '5555', v_org_comp_id),
        ('comp-6',  'Émilie Lavoie',            'Caissier',          'emilie.lavoie@company.com',        '+15145551006', '2024-01-15',
         pin_6666_bcrypt,  encode(digest('6666', 'sha256'), 'hex'), '6666', v_org_comp_id),
        ('comp-7',  'Catherine Pelletier',      'Accueil',           'catherine.pelletier@company.com',  '+15145551007', '2024-01-15',
         pin_7777_bcrypt,  encode(digest('7777', 'sha256'), 'hex'), '7777', v_org_comp_id),
        -- Parc Industriel
        ('comp-8',  'Mathieu Fortin',           'Magasinier',        'mathieu.fortin@company.com',       '+15145551008', '2024-01-15',
         pin_8888_bcrypt,  encode(digest('8888', 'sha256'), 'hex'), '8888', v_org_comp_id),
        ('comp-9',  'Nicolas Bergeron',         'Manutentionnaire',  'nicolas.bergeron@company.com',     '+15145551009', '2024-01-15',
         pin_9999_bcrypt,  encode(digest('9999', 'sha256'), 'hex'), '9999', v_org_comp_id),
        ('comp-10', 'Julie Morin',              'Responsable rayon', 'julie.morin@company.com',          '+15145551010', '2024-01-15',
         pin_1010_bcrypt,  encode(digest('1010', 'sha256'), 'hex'), '1010', v_org_comp_id),
        -- Multi-site (mobile)
        ('comp-11', 'David Gauthier',           'Vendeur',           'david.gauthier@company.com',       '+15145551011', '2024-01-15',
         pin_1122_bcrypt,  encode(digest('1122', 'sha256'), 'hex'), '1122', v_org_comp_id),
        ('comp-12', 'Stéphane Bélanger',        'Agent de sécurité', 'stephane.belanger@company.com',   '+15145551012', '2024-01-15',
         pin_1133_bcrypt,  encode(digest('1133', 'sha256'), 'hex'), '1133', v_org_comp_id);

    -- ============================================================
    -- 5b. MANAGER EMPLOYEE RECORDS (managers are employees too)
    -- ============================================================
    INSERT INTO employe (id, nom, role, email, telephone, date_embauche,
                         pin, pin_hash, pin_clair, organisation_id) VALUES
        -- Entreprise manager
        ('mgr-ent-1', 'Andriantsoa Mika', 'Responsable rayon', 'manager@entreprise.com', '+261341000010', '2023-06-01',
         pin_mgr1_bcrypt, encode(digest('5000', 'sha256'), 'hex'), '5000', v_org_ent_id),
        -- Company managers (Québec)
        ('mgr-comp-1', 'Jean-François Dubois', 'Chef de caisse', 'manager.centre@company.com', '+15145552001', '2023-03-15',
         pin_mgr2_bcrypt, encode(digest('6000', 'sha256'), 'hex'), '6000', v_org_comp_id),
        ('mgr-comp-2', 'Marie-Claire Lévesque', 'Responsable rayon', 'manager.sud@company.com', '+15145552002', '2023-09-01',
         pin_mgr3_bcrypt, encode(digest('7000', 'sha256'), 'hex'), '7000', v_org_comp_id);

    -- Manager disponibilités (Lundi-Vendredi)
    INSERT INTO employe_disponibilites (employe_id, jour, heure_debut, heure_fin) VALUES
        ('mgr-ent-1',  1, 7, 18), ('mgr-ent-1',  2, 7, 18), ('mgr-ent-1',  3, 7, 18), ('mgr-ent-1',  4, 7, 18), ('mgr-ent-1',  5, 7, 18),
        ('mgr-comp-1', 1, 7, 18), ('mgr-comp-1', 2, 7, 18), ('mgr-comp-1', 3, 7, 18), ('mgr-comp-1', 4, 7, 18), ('mgr-comp-1', 5, 7, 18),
        ('mgr-comp-2', 1, 8, 17), ('mgr-comp-2', 2, 8, 17), ('mgr-comp-2', 3, 8, 17), ('mgr-comp-2', 4, 8, 17), ('mgr-comp-2', 5, 8, 17);

    -- Manager site assignments
    INSERT INTO employe_sites (employe_id, site_id) VALUES
        ('mgr-ent-1',  v_site_siege),
        ('mgr-comp-1', v_site_cv),
        ('mgr-comp-2', v_site_bs);

    -- NOTE: Manager banques de congé are inserted AFTER types de congé (section 9b below)

    -- Disponibilités Company
    INSERT INTO employe_disponibilites (employe_id, jour, heure_debut, heure_fin) VALUES
        ('comp-1',  1, 8, 17), ('comp-1',  2, 8, 17), ('comp-1',  3, 8, 17), ('comp-1',  4, 8, 17), ('comp-1',  5, 8, 17),
        ('comp-2',  1, 9, 18), ('comp-2',  2, 9, 18), ('comp-2',  3, 9, 18), ('comp-2',  4, 9, 18), ('comp-2',  5, 9, 18),
        ('comp-3',  1, 8, 17), ('comp-3',  2, 8, 17), ('comp-3',  3, 8, 17), ('comp-3',  4, 8, 17), ('comp-3',  5, 8, 17),
        ('comp-4',  1, 7, 19), ('comp-4',  2, 7, 19), ('comp-4',  3, 7, 19), ('comp-4',  4, 7, 19), ('comp-4',  5, 7, 19),
        ('comp-5',  1, 9, 18), ('comp-5',  2, 9, 18), ('comp-5',  3, 9, 18), ('comp-5',  4, 9, 18), ('comp-5',  5, 9, 18),
        ('comp-6',  1, 8, 17), ('comp-6',  2, 8, 17), ('comp-6',  3, 8, 17), ('comp-6',  4, 8, 17), ('comp-6',  5, 8, 17),
        ('comp-7',  1, 8, 18), ('comp-7',  2, 8, 18), ('comp-7',  3, 8, 18), ('comp-7',  4, 8, 18), ('comp-7',  5, 8, 18),
        ('comp-8',  1, 6, 14), ('comp-8',  2, 6, 14), ('comp-8',  3, 6, 14), ('comp-8',  4, 6, 14), ('comp-8',  5, 6, 14),
        ('comp-9',  1, 6, 14), ('comp-9',  2, 6, 14), ('comp-9',  3, 6, 14), ('comp-9',  4, 6, 14), ('comp-9',  5, 6, 14),
        ('comp-10', 1, 7, 16), ('comp-10', 2, 7, 16), ('comp-10', 3, 7, 16), ('comp-10', 4, 7, 16), ('comp-10', 5, 7, 16),
        ('comp-11', 1, 9, 18), ('comp-11', 2, 9, 18), ('comp-11', 3, 9, 18), ('comp-11', 4, 9, 18), ('comp-11', 5, 9, 18),
        ('comp-12', 1, 7, 19), ('comp-12', 2, 7, 19), ('comp-12', 3, 7, 19), ('comp-12', 4, 7, 19), ('comp-12', 5, 7, 19);

    -- Site assignments Company
    INSERT INTO employe_sites (employe_id, site_id) VALUES
        -- Centre-Ville
        ('comp-1',  v_site_cv),
        ('comp-2',  v_site_cv),
        ('comp-3',  v_site_cv),
        ('comp-4',  v_site_cv),
        -- Banlieue Sud
        ('comp-5',  v_site_bs),
        ('comp-6',  v_site_bs),
        ('comp-7',  v_site_bs),
        -- Zone Industrielle
        ('comp-8',  v_site_zi),
        ('comp-9',  v_site_zi),
        ('comp-10', v_site_zi),
        -- Multi-site (mobile)
        ('comp-11', v_site_cv), ('comp-11', v_site_bs),
        ('comp-12', v_site_cv), ('comp-12', v_site_zi);

    -- ============================================================
    -- 6. USERS (app_user)
    -- ============================================================
    -- Entreprise
    INSERT INTO app_user (email, password, role, employe_id, organisation_id) VALUES
        ('admin@entreprise.com',    h_admin,   'ADMIN',    null,    v_org_ent_id),
        ('manager@entreprise.com',  h_manager, 'MANAGER',  'mgr-ent-1',    v_org_ent_id),
        ('jean.rakoto@entreprise.com',  h_employe, 'EMPLOYEE', 'ent-1', v_org_ent_id),
        ('marie.rabe@entreprise.com',   h_employe, 'EMPLOYEE', 'ent-2', v_org_ent_id),
        ('paul.andria@entreprise.com',  h_employe, 'EMPLOYEE', 'ent-3', v_org_ent_id),
        ('nadia.rasoa@entreprise.com',  h_employe, 'EMPLOYEE', 'ent-4', v_org_ent_id),
        ('hery.randria@entreprise.com', h_employe, 'EMPLOYEE', 'ent-5', v_org_ent_id);

    -- Company (Québec)
    INSERT INTO app_user (email, password, role, employe_id, organisation_id) VALUES
        ('admin@company.com',              h_admin,   'ADMIN',    null,          v_org_comp_id),
        ('manager.centre@company.com',     h_manager, 'MANAGER',  'mgr-comp-1', v_org_comp_id),
        ('manager.sud@company.com',        h_manager, 'MANAGER',  'mgr-comp-2', v_org_comp_id),
        ('marc.tremblay@company.com',      h_employe, 'EMPLOYEE', 'comp-1',     v_org_comp_id),
        ('sophie.gagnon@company.com',      h_employe, 'EMPLOYEE', 'comp-2',     v_org_comp_id),
        ('isabelle.roy@company.com',       h_employe, 'EMPLOYEE', 'comp-3',     v_org_comp_id),
        ('pierre.bouchard@company.com',    h_employe, 'EMPLOYEE', 'comp-4',     v_org_comp_id),
        ('alexandre.cote@company.com',     h_employe, 'EMPLOYEE', 'comp-5',     v_org_comp_id),
        ('emilie.lavoie@company.com',      h_employe, 'EMPLOYEE', 'comp-6',     v_org_comp_id),
        ('catherine.pelletier@company.com',h_employe, 'EMPLOYEE', 'comp-7',     v_org_comp_id),
        ('mathieu.fortin@company.com',     h_employe, 'EMPLOYEE', 'comp-8',     v_org_comp_id),
        ('nicolas.bergeron@company.com',   h_employe, 'EMPLOYEE', 'comp-9',     v_org_comp_id),
        ('julie.morin@company.com',        h_employe, 'EMPLOYEE', 'comp-10',    v_org_comp_id),
        ('david.gauthier@company.com',     h_employe, 'EMPLOYEE', 'comp-11',    v_org_comp_id),
        ('stephane.belanger@company.com',  h_employe, 'EMPLOYEE', 'comp-12',    v_org_comp_id);

    -- SUPERADMIN (platform-level — no org)
    -- WARNING: Change password immediately in production.
    -- Set SUPERADMIN_PASSWORD env var and re-seed, or update manually.
    INSERT INTO app_user (email, password, role, employe_id, organisation_id) VALUES
        ('superadmin@schedy.io', h_superadmin, 'SUPERADMIN', null, null);

    -- ============================================================
    -- 7. EXIGENCES
    -- ============================================================
    -- Siège Entreprise
    INSERT INTO exigence (id, libelle, heure_debut, heure_fin, role, nombre_requis, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'Caisse matin',       8,  13, 'Caissier',         2, v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'Vente après-midi',  13,  18, 'Vendeur',          1, v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'Manutention matin',  6,  12, 'Manutentionnaire', 1, v_site_siege, v_org_ent_id);

    -- Centre-Ville Company
    INSERT INTO exigence (id, libelle, heure_debut, heure_fin, role, nombre_requis, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'Caisse journée',    8,  18, 'Caissier',         2, v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'Vente matin',       9,  13, 'Vendeur',          2, v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'Vente après-midi', 13,  18, 'Vendeur',          2, v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'Sécurité',          7,  19, 'Agent de sécurité',1, v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'Accueil',           8,  18, 'Accueil',          1, v_site_cv, v_org_comp_id);

    -- Banlieue Sud Company
    INSERT INTO exigence (id, libelle, heure_debut, heure_fin, role, nombre_requis, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'Caisse',  9, 17, 'Caissier', 1, v_site_bs, v_org_comp_id),
        (gen_random_uuid(), 'Vente',   9, 17, 'Vendeur',  1, v_site_bs, v_org_comp_id),
        (gen_random_uuid(), 'Accueil', 9, 17, 'Accueil',  1, v_site_bs, v_org_comp_id);

    -- Zone Industrielle Company
    INSERT INTO exigence (id, libelle, heure_debut, heure_fin, role, nombre_requis, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'Manutention matin', 6,  12, 'Manutentionnaire',  2, v_site_zi, v_org_comp_id),
        (gen_random_uuid(), 'Magasin',           6,  14, 'Magasinier',        1, v_site_zi, v_org_comp_id),
        (gen_random_uuid(), 'Supervision',       7,  16, 'Responsable rayon', 1, v_site_zi, v_org_comp_id);

    -- Exigence jours (Lundi-Vendredi for all — join via site_id + libelle not possible with UUIDs)
    -- We insert jours for all exigences in this org using a helper approach
    INSERT INTO exigence_jours (exigence_id, jour)
    SELECT e.id, j.jour
    FROM exigence e
    CROSS JOIN (SELECT 1 AS jour UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) j
    WHERE e.organisation_id IN (v_org_ent_id, v_org_comp_id);

    -- ============================================================
    -- 8. TYPES DE CONGE (4 per organisation)
    -- ============================================================
    INSERT INTO type_conge (id, nom, categorie, unite, couleur, mode_quota, quota_illimite, autoriser_negatif, organisation_id) VALUES
        -- Entreprise
        (v_tc_cp_ent,   'Congé payé',       'paye',    'jours', '#4CAF50', 'annuel',    false, false, v_org_ent_id),
        (v_tc_css_ent,  'Congé sans solde', 'non_paye','jours', '#FF9800', 'illimite',  true,  false, v_org_ent_id),
        (v_tc_am_ent,   'Arrêt maladie',    'paye',    'jours', '#F44336', 'illimite',  true,  false, v_org_ent_id),
        (v_tc_mat_ent,  'Congé maternité',  'paye',    'jours', '#E91E63', 'evenement', false, false, v_org_ent_id),
        -- Company
        (v_tc_cp_comp,  'Congé payé',       'paye',    'jours', '#4CAF50', 'annuel',    false, false, v_org_comp_id),
        (v_tc_css_comp, 'Congé sans solde', 'non_paye','jours', '#FF9800', 'illimite',  true,  false, v_org_comp_id),
        (v_tc_am_comp,  'Arrêt maladie',    'paye',    'jours', '#F44336', 'illimite',  true,  false, v_org_comp_id),
        (v_tc_mat_comp, 'Congé maternité',  'paye',    'jours', '#E91E63', 'evenement', false, false, v_org_comp_id);

    -- ============================================================
    -- 9. BANQUES DE CONGE (congé payé only — 20 jours/an)
    -- version = 0 for initial records (optimistic locking)
    -- ============================================================
    -- Entreprise employees
    INSERT INTO banque_conge (id, employe_id, type_conge_id, quota, utilise, en_attente, date_debut, date_fin, organisation_id, version) VALUES
        (gen_random_uuid(), 'ent-1', v_tc_cp_ent, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_ent_id, 0),
        (gen_random_uuid(), 'ent-2', v_tc_cp_ent, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_ent_id, 0),
        (gen_random_uuid(), 'ent-3', v_tc_cp_ent, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_ent_id, 0),
        (gen_random_uuid(), 'ent-4', v_tc_cp_ent, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_ent_id, 0),
        (gen_random_uuid(), 'ent-5', v_tc_cp_ent, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_ent_id, 0);

    -- Company employees
    INSERT INTO banque_conge (id, employe_id, type_conge_id, quota, utilise, en_attente, date_debut, date_fin, organisation_id, version) VALUES
        (gen_random_uuid(), 'comp-1',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-2',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-3',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-4',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-5',  v_tc_cp_comp, 20, 2, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-6',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-7',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-8',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-9',  v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-10', v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-11', v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'comp-12', v_tc_cp_comp, 20, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0);

    -- 9b. Manager banques de congé (25 jours — more than regular employees)
    INSERT INTO banque_conge (id, employe_id, type_conge_id, quota, utilise, en_attente, date_debut, date_fin, organisation_id, version) VALUES
        (gen_random_uuid(), 'mgr-ent-1',  v_tc_cp_ent,  25, 0, 0, '2026-01-01', '2026-12-31', v_org_ent_id,  0),
        (gen_random_uuid(), 'mgr-comp-1', v_tc_cp_comp, 25, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0),
        (gen_random_uuid(), 'mgr-comp-2', v_tc_cp_comp, 25, 0, 0, '2026-01-01', '2026-12-31', v_org_comp_id, 0);

    -- ============================================================
    -- 10. JOURS FERIES
    -- ============================================================
    -- Entreprise (Madagascar)
    INSERT INTO jour_ferie (id, nom, date, recurrent, organisation_id) VALUES
        (gen_random_uuid(), 'Nouvel An',                '2026-01-01', true,  v_org_ent_id),
        (gen_random_uuid(), 'Journée des Martyrs',      '2026-03-29', true,  v_org_ent_id),
        (gen_random_uuid(), 'Fête du Travail',          '2026-05-01', true,  v_org_ent_id),
        (gen_random_uuid(), 'Fête de l''Indépendance',  '2026-06-26', true,  v_org_ent_id),
        (gen_random_uuid(), 'Assomption',               '2026-08-15', true,  v_org_ent_id),
        (gen_random_uuid(), 'Toussaint',                '2026-11-01', true,  v_org_ent_id),
        (gen_random_uuid(), 'Noël',                     '2026-12-25', true,  v_org_ent_id);

    -- Company (Quebec)
    INSERT INTO jour_ferie (id, nom, date, recurrent, organisation_id) VALUES
        (gen_random_uuid(), 'Jour de l''An',                         '2026-01-01', true,  v_org_comp_id),
        (gen_random_uuid(), 'Vendredi saint',                        '2026-04-03', false, v_org_comp_id),
        (gen_random_uuid(), 'Lundi de Pâques',                       '2026-04-06', false, v_org_comp_id),
        (gen_random_uuid(), 'Journée nationale des Patriotes',       '2026-05-18', false, v_org_comp_id),
        (gen_random_uuid(), 'Fête nationale du Québec',              '2026-06-24', true,  v_org_comp_id),
        (gen_random_uuid(), 'Fête du Canada',                        '2026-07-01', true,  v_org_comp_id),
        (gen_random_uuid(), 'Fête du Travail',                       '2026-09-07', false, v_org_comp_id),
        (gen_random_uuid(), 'Action de grâces',                      '2026-10-12', false, v_org_comp_id),
        (gen_random_uuid(), 'Noël',                                  '2026-12-25', true,  v_org_comp_id);

    -- ============================================================
    -- 11. PARAMETRES (1 per site)
    -- ============================================================
    INSERT INTO parametres (heure_debut, heure_fin, premier_jour, duree_min_affectation,
                             heures_max_semaine, site_id, organisation_id,
                             taille_police, planning_vue, planning_granularite) VALUES
        (6, 22, 1, 1.0, 48.0, v_site_siege, v_org_ent_id,  'petit', 'horaire', 1.0),
        (6, 22, 1, 1.0, 48.0, v_site_cv,    v_org_comp_id, 'petit', 'horaire', 1.0),
        (6, 22, 1, 1.0, 48.0, v_site_bs,    v_org_comp_id, 'petit', 'horaire', 1.0),
        (6, 22, 1, 1.0, 48.0, v_site_zi,    v_org_comp_id, 'petit', 'horaire', 1.0);

    -- Jours actifs (Lundi-Vendredi) for all parametres rows just inserted
    INSERT INTO parametres_jours_actifs (parametres_id, jour)
    SELECT p.id, j.jour
    FROM parametres p
    CROSS JOIN (SELECT 1 AS jour UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) j
    WHERE p.site_id IN (v_site_siege, v_site_cv, v_site_bs, v_site_zi);

    -- ============================================================
    -- 12. CRENEAUX ASSIGNE (current week — computed dynamically)
    -- ============================================================
    -- Siège Entreprise — Lundi à Vendredi
    INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id)
    SELECT gen_random_uuid(), emp, j, hd, hf, v_semaine, v_site_siege, v_org_ent_id
    FROM (VALUES
        ('ent-1', 8,  13),
        ('ent-4', 8,  13),
        ('ent-2', 13, 18),
        ('ent-5', 6,  12)
    ) AS t(emp, hd, hf)
    CROSS JOIN generate_series(1, 5) AS j;

    -- Centre-Ville Company — Lundi à Vendredi
    INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id)
    SELECT gen_random_uuid(), emp, j, hd, hf, v_semaine, v_site_cv, v_org_comp_id
    FROM (VALUES
        ('comp-1',  8,  18),
        ('comp-3',  8,  17),
        ('comp-2',  9,  13),
        ('comp-11', 13, 18),
        ('comp-4',  7,  19)
    ) AS t(emp, hd, hf)
    CROSS JOIN generate_series(1, 5) AS j;

    -- Banlieue Sud Company — Lundi à Vendredi
    INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id)
    SELECT gen_random_uuid(), emp, j, hd, hf, v_semaine, v_site_bs, v_org_comp_id
    FROM (VALUES
        ('comp-6', 9, 17),
        ('comp-7', 9, 17)
    ) AS t(emp, hd, hf)
    CROSS JOIN generate_series(1, 5) AS j;

    -- comp-5 (Isabelle Bouchard): Lundi-Mercredi seulement (jeudi-vendredi en congé)
    INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id)
    SELECT gen_random_uuid(), 'comp-5', j, 9, 17, v_semaine, v_site_bs, v_org_comp_id
    FROM generate_series(1, 3) AS j;

    -- Zone Industrielle Company — Lundi à Vendredi
    INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id)
    SELECT gen_random_uuid(), emp, j, hd, hf, v_semaine, v_site_zi, v_org_comp_id
    FROM (VALUES
        ('comp-9',  6, 12),
        ('comp-12', 6, 12),
        ('comp-8',  6, 14),
        ('comp-10', 7, 16)
    ) AS t(emp, hd, hf)
    CROSS JOIN generate_series(1, 5) AS j;

    -- ============================================================
    -- 13. POINTAGES DE TEST (fixed date: 2026-03-20)
    -- ============================================================
    -- Company — Centre-Ville
    INSERT INTO pointage (id, employe_id, type, horodatage, methode, statut, anomalie, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'comp-1', 'entree', '2026-03-20 07:52:00+00', 'pin', 'valide',   null,              v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'comp-1', 'sortie', '2026-03-20 18:00:00+00', 'pin', 'valide',   null,              v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'comp-2', 'entree', '2026-03-20 09:05:00+00', 'qr',  'valide',   null,              v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'comp-2', 'sortie', '2026-03-20 13:00:00+00', 'qr',  'valide',   null,              v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'comp-3', 'entree', '2026-03-20 08:00:00+00', 'web', 'valide',   null,              v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'comp-3', 'sortie', '2026-03-20 17:02:00+00', 'web', 'valide',   null,              v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'comp-4', 'entree', '2026-03-20 06:55:00+00', 'pin', 'valide',   null,              v_site_cv, v_org_comp_id),
        (gen_random_uuid(), 'comp-4', 'sortie', '2026-03-20 19:05:00+00', 'pin', 'valide',   null,              v_site_cv, v_org_comp_id);

    -- Company — Banlieue Sud
    INSERT INTO pointage (id, employe_id, type, horodatage, methode, statut, anomalie, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'comp-6', 'entree', '2026-03-20 08:55:00+00', 'pin', 'valide',   null,           v_site_bs, v_org_comp_id),
        (gen_random_uuid(), 'comp-6', 'sortie', '2026-03-20 17:00:00+00', 'pin', 'valide',   null,           v_site_bs, v_org_comp_id),
        (gen_random_uuid(), 'comp-5', 'entree', '2026-03-20 09:10:00+00', 'pin', 'anomalie', 'Retard 10min', v_site_bs, v_org_comp_id),
        (gen_random_uuid(), 'comp-5', 'sortie', '2026-03-20 17:05:00+00', 'pin', 'valide',   null,           v_site_bs, v_org_comp_id);

    -- Company — Zone Industrielle
    INSERT INTO pointage (id, employe_id, type, horodatage, methode, statut, anomalie, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'comp-9', 'entree', '2026-03-20 05:58:00+00', 'pin', 'valide', null, v_site_zi, v_org_comp_id),
        (gen_random_uuid(), 'comp-9', 'sortie', '2026-03-20 12:02:00+00', 'pin', 'valide', null, v_site_zi, v_org_comp_id),
        (gen_random_uuid(), 'comp-8', 'entree', '2026-03-20 06:05:00+00', 'pin', 'valide', null, v_site_zi, v_org_comp_id),
        (gen_random_uuid(), 'comp-8', 'sortie', '2026-03-20 14:00:00+00', 'pin', 'valide', null, v_site_zi, v_org_comp_id);

    -- Entreprise — Siège
    INSERT INTO pointage (id, employe_id, type, horodatage, methode, statut, anomalie, site_id, organisation_id) VALUES
        (gen_random_uuid(), 'ent-1', 'entree', '2026-03-20 07:55:00+00', 'pin', 'valide',   null,           v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'ent-1', 'sortie', '2026-03-20 17:02:00+00', 'pin', 'valide',   null,           v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'ent-2', 'entree', '2026-03-20 09:00:00+00', 'qr',  'valide',   null,           v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'ent-2', 'sortie', '2026-03-20 13:05:00+00', 'qr',  'valide',   null,           v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'ent-4', 'entree', '2026-03-20 06:50:00+00', 'pin', 'valide',   null,           v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'ent-4', 'sortie', '2026-03-20 19:10:00+00', 'pin', 'valide',   null,           v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'ent-5', 'entree', '2026-03-20 07:15:00+00', 'pin', 'anomalie', 'Retard 15min', v_site_siege, v_org_ent_id),
        (gen_random_uuid(), 'ent-5', 'sortie', '2026-03-20 15:00:00+00', 'pin', 'valide',   null,           v_site_siege, v_org_ent_id);

    -- ============================================================
    -- 14. DEMANDES DE CONGE DE TEST
    -- ============================================================
    INSERT INTO demande_conge (id, employe_id, type_conge_id, date_debut, date_fin, duree, statut, motif, organisation_id) VALUES
        -- Rabe Marie (ent-2): 3 jours congé payé dans 7 jours (approuvé)
        (gen_random_uuid(), 'ent-2', v_tc_cp_ent,
         v_today + 7, v_today + 9,
         3, 'approuve', 'Vacances familiales', v_org_ent_id),
        -- Rakoto Jean (ent-1): 2 jours congé payé dans 21 jours (en attente)
        (gen_random_uuid(), 'ent-1', v_tc_cp_ent,
         v_today + 21, v_today + 22,
         2, 'en_attente', 'Affaires personnelles', v_org_ent_id),
        -- Razafin Soa (comp-2): 5 jours congé payé dans 14 jours (en attente)
        (gen_random_uuid(), 'comp-2', v_tc_cp_comp,
         v_today + 14, v_today + 18,
         5, 'en_attente', 'Voyage personnel', v_org_comp_id),
        -- Nicolas Bergeron (comp-9): Arrêt maladie 2 jours semaine dernière (approuvé)
        (gen_random_uuid(), 'comp-9', v_tc_am_comp,
         v_today - 9, v_today - 8,
         2, 'approuve', 'Certificat medical fourni', v_org_comp_id),
        -- Alexandre Côté (comp-5): Congé payé jeudi-vendredi cette semaine (approuvé)
        (gen_random_uuid(), 'comp-5', v_tc_cp_comp,
         v_today + (4 - extract(isodow from v_today)::int),
         v_today + (5 - extract(isodow from v_today)::int),
         2, 'approuve', 'Week-end prolongé', v_org_comp_id),
        -- Andrianaivo Tiana (comp-7): 1 jour congé payé dans 8 jours (refusé)
        (gen_random_uuid(), 'comp-7', v_tc_cp_comp,
         v_today + 8, v_today + 8,
         1, 'refuse', 'Rendez-vous personnel', v_org_comp_id);

    -- ============================================================
    -- 15. POINTAGE CODES (1 per site, valid for 1 year from today)
    -- NOTE: The actual rotation logic is handled by the scheduler.
    --       These are initial codes to allow dev testing without waiting for rotation.
    -- ============================================================
    INSERT INTO pointage_code (id, site_id, code, pin, pin_hash, rotation_valeur, rotation_unite,
                                valid_from, valid_to, actif, organisation_id) VALUES
        (gen_random_uuid(), v_site_siege,
         upper(substring(md5(random()::text) for 8)),
         lpad(floor(random() * 1000000)::text, 6, '0'),
         encode(digest(lpad(floor(random() * 1000000)::text, 6, '0'), 'sha256'), 'hex'),
         1, 'JOURS',
         now(), now() + interval '1 year', true, v_org_ent_id),

        (gen_random_uuid(), v_site_cv,
         upper(substring(md5(random()::text) for 8)),
         lpad(floor(random() * 1000000)::text, 6, '0'),
         encode(digest(lpad(floor(random() * 1000000)::text, 6, '0'), 'sha256'), 'hex'),
         1, 'JOURS',
         now(), now() + interval '1 year', true, v_org_comp_id),

        (gen_random_uuid(), v_site_bs,
         upper(substring(md5(random()::text) for 8)),
         lpad(floor(random() * 1000000)::text, 6, '0'),
         encode(digest(lpad(floor(random() * 1000000)::text, 6, '0'), 'sha256'), 'hex'),
         1, 'JOURS',
         now(), now() + interval '1 year', true, v_org_comp_id),

        (gen_random_uuid(), v_site_zi,
         upper(substring(md5(random()::text) for 8)),
         lpad(floor(random() * 1000000)::text, 6, '0'),
         encode(digest(lpad(floor(random() * 1000000)::text, 6, '0'), 'sha256'), 'hex'),
         1, 'JOURS',
         now(), now() + interval '1 year', true, v_org_comp_id);

    -- ============================================================
    -- 16. SUBSCRIPTIONS (FREE/TRIAL per organisation)
    -- ============================================================
    INSERT INTO subscription (id, organisation_id, plan_tier, status,
                               trial_ends_at, max_employees, max_sites, created_at, updated_at) VALUES
        (gen_random_uuid(), v_org_ent_id,  'FREE', 'TRIAL',
         now() + interval '90 days', 15, 1, now(), now()),
        (gen_random_uuid(), v_org_comp_id, 'FREE', 'TRIAL',
         now() + interval '90 days', 15, 3, now(), now());

    -- ============================================================
    -- 17. BETA PROMO CODES
    -- ============================================================
    INSERT INTO promo_code (id, code, description, discount_percent, discount_months,
                             plan_override, max_uses, current_uses, active, valid_from, created_at) VALUES
        (gen_random_uuid(), 'BETA2026',   '100% de réduction pendant 3 mois — plan PRO',  100, 3,  'PRO', 50,  0, true, now(), now()),
        (gen_random_uuid(), 'EARLYBIRD',  '50% de réduction pendant 6 mois',               50,  6,  null,  100, 0, true, now(), now()),
        (gen_random_uuid(), 'MADAGASCAR', '100% de réduction pendant 12 mois — plan PRO', 100, 12, 'PRO', 20,  0, true, now(), now());

    RAISE NOTICE 'Seed data inserted successfully.';
    RAISE NOTICE '  Organisations : 2 (Entreprise + Company)';
    RAISE NOTICE '  Sites          : 4 (1 + 3)';
    RAISE NOTICE '  Employes       : 17 (5 + 12)';
    RAISE NOTICE '  Users          : 22 (7 ent + 14 comp + 1 superadmin)';
    RAISE NOTICE '  Exigences      : 14 (3 siege + 8 cv + 3 bs + 3 zi — approx)';
    RAISE NOTICE '  Types conge    : 8 (4 per org)';
    RAISE NOTICE '  Banques conge  : 17 (5 ent + 12 comp)';
    RAISE NOTICE '  Creneaux       : current week (%)', v_semaine;
    RAISE NOTICE '  Pointages      : 24 (2026-03-20)';
    RAISE NOTICE '  Promo codes    : BETA2026, EARLYBIRD, MADAGASCAR';
    RAISE NOTICE '  Subscriptions  : 2 (FREE/TRIAL)';

END $$;

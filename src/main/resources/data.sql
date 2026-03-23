-- ============================================
-- Seed data for Schedy (H2 dev profile)
-- Multi-site, multi-organisation support
-- ============================================
--
-- IMPORTANT: Ce fichier est charge UNIQUEMENT en profil dev (H2).
-- En prod (PostgreSQL), DataInitializer.java cree toutes les donnees.
-- Si DataInitializer detecte des organisations en base, il ne s'execute pas.
-- Donc ce data.sql et DataInitializer sont mutuellement exclusifs.
--
-- Comptes de test:
--
--   COMPANY (Quebec, 3 sites):
--     admin@company.com              / admin123   (ADMIN)
--     manager@company.com            / manager123 (MANAGER)
--     marc.tremblay@company.com      / employe123 (EMPLOYEE, comp-1)
--     sophie.gagnon@company.com      / employe123 (EMPLOYEE, comp-2)
--     ... (tous les employes: employe123)
--
--   ENTREPRISE (Madagascar, 1 site):
--     admin@entreprise.com           / admin123   (ADMIN)
--     manager@entreprise.com         / manager123 (MANAGER)
--     luc.raharison@entreprise.com   / employe123 (EMPLOYEE, ent-1)
--     soa.razafindrakoto@entreprise.com / employe123 (EMPLOYEE, ent-2)
--     ... (tous les employes: employe123)
--

-- =====================
-- Organisations
-- =====================
INSERT INTO organisation (id, nom, domaine) VALUES
('org-1', 'Company', 'company.com'),
('org-2', 'Entreprise', 'entreprise.com') ON CONFLICT DO NOTHING;

-- =====================
-- Sites
-- =====================
-- Company (org-1): 3 sites au Quebec
INSERT INTO site (id, nom, adresse, telephone, actif, organisation_id) VALUES
('site-centre', 'Centre-Ville', '150 Rue Sainte-Catherine Est, Montreal, QC', '+1 514 555-0100', true, 'org-1'),
('site-banlieue', 'Banlieue Sud', '3200 Boulevard Taschereau, Longueuil, QC', '+1 450 555-0200', true, 'org-1'),
('site-zi', 'Zone Industrielle', '8500 Boulevard Henri-Bourassa, Quebec, QC', '+1 418 555-0300', true, 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2): 1 site a Madagascar
INSERT INTO site (id, nom, adresse, telephone, actif, organisation_id) VALUES
('site-siege', 'Siege Entreprise', 'Lot II J 45 Ankorondrano, Antananarivo 101', '+261 20 22 345 67', true, 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Roles
-- =====================
-- Company (org-1)
INSERT INTO role (id, nom, importance, couleur, organisation_id) VALUES
('role-1', 'Caissier', 1, '#4CAF50', 'org-1'),
('role-2', 'Vendeur', 2, '#2196F3', 'org-1'),
('role-3', 'Responsable rayon', 3, '#FF9800', 'org-1'),
('role-4', 'Agent de securite', 4, '#F44336', 'org-1'),
('role-5', 'Manutentionnaire', 5, '#795548', 'org-1'),
('role-6', 'Accueil', 6, '#9C27B0', 'org-1'),
('role-7', 'Chef de caisse', 7, '#E91E63', 'org-1'),
('role-8', 'Magasinier', 8, '#607D8B', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2)
INSERT INTO role (id, nom, importance, couleur, organisation_id) VALUES
('role-e1', 'Caissier', 1, '#4CAF50', 'org-2'),
('role-e2', 'Vendeur', 2, '#2196F3', 'org-2'),
('role-e3', 'Responsable rayon', 3, '#FF9800', 'org-2'),
('role-e4', 'Agent de securite', 4, '#F44336', 'org-2'),
('role-e5', 'Accueil', 5, '#9C27B0', 'org-2'),
('role-e6', 'Magasinier', 6, '#607D8B', 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Employes
-- =====================
-- Company (org-1): 12 employes, noms francais, tel quebecois
INSERT INTO employe (id, nom, role, telephone, email, date_naissance, date_embauche, pin, organisation_id) VALUES
-- Centre-Ville
('comp-1', 'Tremblay Marc', 'Caissier', '+1 514 555-0001', 'marc.tremblay@company.com', '1990-03-15', '2024-01-15', '1111', 'org-1'),
('comp-2', 'Gagnon Sophie', 'Vendeur', '+1 514 555-0002', 'sophie.gagnon@company.com', '1988-07-22', '2024-01-15', '2222', 'org-1'),
('comp-3', 'Roy Isabelle', 'Chef de caisse', '+1 514 555-0003', 'isabelle.roy@company.com', '1985-11-05', '2024-01-15', '3333', 'org-1'),
('comp-4', 'Bouchard Pierre', 'Agent de securite', '+1 514 555-0004', 'pierre.bouchard@company.com', '1995-01-30', '2024-01-15', '4444', 'org-1'),
-- Banlieue Sud
('comp-5', 'Cote Alexandre', 'Vendeur', '+1 450 555-0005', 'alexandre.cote@company.com', '1992-09-12', '2024-01-15', '5555', 'org-1'),
('comp-6', 'Lavoie Emilie', 'Caissier', '+1 450 555-0006', 'emilie.lavoie@company.com', '1991-04-18', '2024-01-15', '6666', 'org-1'),
('comp-7', 'Pelletier Catherine', 'Accueil', '+1 450 555-0007', 'catherine.pelletier@company.com', '1987-12-03', '2024-01-15', '7777', 'org-1'),
-- Zone Industrielle
('comp-8', 'Fortin Mathieu', 'Magasinier', '+1 418 555-0008', 'mathieu.fortin@company.com', '1993-06-25', '2024-01-15', '8888', 'org-1'),
('comp-9', 'Bergeron Nicolas', 'Manutentionnaire', '+1 418 555-0009', 'nicolas.bergeron@company.com', '1989-08-14', '2024-01-15', '9999', 'org-1'),
('comp-10', 'Morin Julie', 'Responsable rayon', '+1 418 555-0010', 'julie.morin@company.com', '1994-02-28', '2024-01-15', '1010', 'org-1'),
-- Multi-sites (mobiles)
('comp-11', 'Gauthier David', 'Vendeur', '+1 514 555-0011', 'david.gauthier@company.com', '1990-06-10', '2024-01-15', '1122', 'org-1'),
('comp-12', 'Belanger Stephane', 'Agent de securite', '+1 514 555-0012', 'stephane.belanger@company.com', '1992-03-20', '2024-01-15', '1133', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2): 8 employes, noms malgaches, tel malgaches
INSERT INTO employe (id, nom, role, telephone, email, date_naissance, date_embauche, pin, organisation_id) VALUES
('ent-1', 'Raharison Luc', 'Caissier', '+261 32 45 678 90', 'luc.raharison@entreprise.com', '1990-05-10', '2024-03-01', '1111', 'org-2'),
('ent-2', 'Razafindrakoto Soa', 'Vendeur', '+261 33 12 345 67', 'soa.razafindrakoto@entreprise.com', '1988-09-15', '2024-03-01', '2222', 'org-2'),
('ent-3', 'Ravelonanahary Aina', 'Responsable rayon', '+261 34 56 789 01', 'aina.ravelonanahary@entreprise.com', '1985-02-20', '2024-03-01', '3333', 'org-2'),
('ent-4', 'Randriamanantsoa Fidy', 'Agent de securite', '+261 32 78 901 23', 'fidy.randriamanantsoa@entreprise.com', '1995-07-08', '2024-03-01', '4444', 'org-2'),
('ent-5', 'Rakotomalala Haja', 'Vendeur', '+261 33 23 456 78', 'haja.rakotomalala@entreprise.com', '1992-11-25', '2024-03-01', '5555', 'org-2'),
('ent-6', 'Rasoarimanana Vola', 'Accueil', '+261 34 34 567 89', 'vola.rasoarimanana@entreprise.com', '1991-01-12', '2024-03-01', '6666', 'org-2'),
('ent-7', 'Andrianaivomanana Tiana', 'Magasinier', '+261 32 67 890 12', 'tiana.andrianaivomanana@entreprise.com', '1987-06-30', '2024-03-01', '7777', 'org-2'),
('ent-8', 'Rajaonarivelo Mamy', 'Caissier', '+261 33 89 012 34', 'mamy.rajaonarivelo@entreprise.com', '1993-04-17', '2024-03-01', '8888', 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Employe site assignments
-- =====================
-- Company: comp-1 to comp-4: site-centre
--          comp-5 to comp-7: site-banlieue
--          comp-8 to comp-10: site-zi
--          comp-11: centre + banlieue (mobile)
--          comp-12: centre + zi (mobile)
INSERT INTO employe_sites (employe_id, site_id) VALUES
('comp-1', 'site-centre'), ('comp-2', 'site-centre'), ('comp-3', 'site-centre'), ('comp-4', 'site-centre'),
('comp-5', 'site-banlieue'), ('comp-6', 'site-banlieue'), ('comp-7', 'site-banlieue'),
('comp-8', 'site-zi'), ('comp-9', 'site-zi'), ('comp-10', 'site-zi'),
('comp-11', 'site-centre'), ('comp-11', 'site-banlieue'),
('comp-12', 'site-centre'), ('comp-12', 'site-zi') ON CONFLICT DO NOTHING;

-- Entreprise: tous sur site-siege
INSERT INTO employe_sites (employe_id, site_id) VALUES
('ent-1', 'site-siege'), ('ent-2', 'site-siege'), ('ent-3', 'site-siege'), ('ent-4', 'site-siege'),
('ent-5', 'site-siege'), ('ent-6', 'site-siege'), ('ent-7', 'site-siege'), ('ent-8', 'site-siege') ON CONFLICT DO NOTHING;

-- =====================
-- Disponibilites (Lundi-Vendredi pour tous)
-- =====================
-- Company
INSERT INTO employe_disponibilites (employe_id, jour, heure_debut, heure_fin) VALUES
-- comp-1: 8h-17h
('comp-1', 1, 8, 17), ('comp-1', 2, 8, 17), ('comp-1', 3, 8, 17), ('comp-1', 4, 8, 17), ('comp-1', 5, 8, 17),
-- comp-2: 9h-18h
('comp-2', 1, 9, 18), ('comp-2', 2, 9, 18), ('comp-2', 3, 9, 18), ('comp-2', 4, 9, 18), ('comp-2', 5, 9, 18),
-- comp-3: 8h-17h
('comp-3', 1, 8, 17), ('comp-3', 2, 8, 17), ('comp-3', 3, 8, 17), ('comp-3', 4, 8, 17), ('comp-3', 5, 8, 17),
-- comp-4: 7h-19h
('comp-4', 1, 7, 19), ('comp-4', 2, 7, 19), ('comp-4', 3, 7, 19), ('comp-4', 4, 7, 19), ('comp-4', 5, 7, 19),
-- comp-5: 9h-18h
('comp-5', 1, 9, 18), ('comp-5', 2, 9, 18), ('comp-5', 3, 9, 18), ('comp-5', 4, 9, 18), ('comp-5', 5, 9, 18),
-- comp-6: 8h-17h
('comp-6', 1, 8, 17), ('comp-6', 2, 8, 17), ('comp-6', 3, 8, 17), ('comp-6', 4, 8, 17), ('comp-6', 5, 8, 17),
-- comp-7: 8h-18h
('comp-7', 1, 8, 18), ('comp-7', 2, 8, 18), ('comp-7', 3, 8, 18), ('comp-7', 4, 8, 18), ('comp-7', 5, 8, 18),
-- comp-8: 6h-14h
('comp-8', 1, 6, 14), ('comp-8', 2, 6, 14), ('comp-8', 3, 6, 14), ('comp-8', 4, 6, 14), ('comp-8', 5, 6, 14),
-- comp-9: 6h-14h
('comp-9', 1, 6, 14), ('comp-9', 2, 6, 14), ('comp-9', 3, 6, 14), ('comp-9', 4, 6, 14), ('comp-9', 5, 6, 14),
-- comp-10: 7h-16h
('comp-10', 1, 7, 16), ('comp-10', 2, 7, 16), ('comp-10', 3, 7, 16), ('comp-10', 4, 7, 16), ('comp-10', 5, 7, 16),
-- comp-11: 9h-18h
('comp-11', 1, 9, 18), ('comp-11', 2, 9, 18), ('comp-11', 3, 9, 18), ('comp-11', 4, 9, 18), ('comp-11', 5, 9, 18),
-- comp-12: 7h-19h
('comp-12', 1, 7, 19), ('comp-12', 2, 7, 19), ('comp-12', 3, 7, 19), ('comp-12', 4, 7, 19), ('comp-12', 5, 7, 19) ON CONFLICT DO NOTHING;

-- Entreprise
INSERT INTO employe_disponibilites (employe_id, jour, heure_debut, heure_fin) VALUES
-- ent-1: 8h-17h
('ent-1', 1, 8, 17), ('ent-1', 2, 8, 17), ('ent-1', 3, 8, 17), ('ent-1', 4, 8, 17), ('ent-1', 5, 8, 17),
-- ent-2: 9h-18h
('ent-2', 1, 9, 18), ('ent-2', 2, 9, 18), ('ent-2', 3, 9, 18), ('ent-2', 4, 9, 18), ('ent-2', 5, 9, 18),
-- ent-3: 8h-17h
('ent-3', 1, 8, 17), ('ent-3', 2, 8, 17), ('ent-3', 3, 8, 17), ('ent-3', 4, 8, 17), ('ent-3', 5, 8, 17),
-- ent-4: 7h-19h
('ent-4', 1, 7, 19), ('ent-4', 2, 7, 19), ('ent-4', 3, 7, 19), ('ent-4', 4, 7, 19), ('ent-4', 5, 7, 19),
-- ent-5: 9h-18h
('ent-5', 1, 9, 18), ('ent-5', 2, 9, 18), ('ent-5', 3, 9, 18), ('ent-5', 4, 9, 18), ('ent-5', 5, 9, 18),
-- ent-6: 8h-17h
('ent-6', 1, 8, 17), ('ent-6', 2, 8, 17), ('ent-6', 3, 8, 17), ('ent-6', 4, 8, 17), ('ent-6', 5, 8, 17),
-- ent-7: 7h-15h
('ent-7', 1, 7, 15), ('ent-7', 2, 7, 15), ('ent-7', 3, 7, 15), ('ent-7', 4, 7, 15), ('ent-7', 5, 7, 15),
-- ent-8: 8h-17h
('ent-8', 1, 8, 17), ('ent-8', 2, 8, 17), ('ent-8', 3, 8, 17), ('ent-8', 4, 8, 17), ('ent-8', 5, 8, 17) ON CONFLICT DO NOTHING;

-- =====================
-- Users (app_user)
-- Passwords: BCrypt encoded
-- =====================
-- Company (org-1)
INSERT INTO app_user (email, password, role, employe_id, organisation_id) VALUES
-- Admin & Manager
('admin@company.com', '$2a$10$JTVf.cpuJySkFKaOf/tTgeyhYLIsJ2Fpg0kkS3SFFc/PWHX5LeQgi', 'ADMIN', null, 'org-1'),
('manager@company.com', '$2a$10$qPsgOcTi0N5tD4KfiwqF0.sXbnyWs20ClmRMZnYqTMs.P3erZMhTu', 'MANAGER', null, 'org-1'),
-- Employes (password: employe123)
('marc.tremblay@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-1', 'org-1'),
('sophie.gagnon@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-2', 'org-1'),
('isabelle.roy@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-3', 'org-1'),
('pierre.bouchard@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-4', 'org-1'),
('alexandre.cote@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-5', 'org-1'),
('emilie.lavoie@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-6', 'org-1'),
('catherine.pelletier@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-7', 'org-1'),
('mathieu.fortin@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-8', 'org-1'),
('nicolas.bergeron@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-9', 'org-1'),
('julie.morin@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-10', 'org-1'),
('david.gauthier@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-11', 'org-1'),
('stephane.belanger@company.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'comp-12', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2)
INSERT INTO app_user (email, password, role, employe_id, organisation_id) VALUES
-- Admin & Manager
('admin@entreprise.com', '$2a$10$JTVf.cpuJySkFKaOf/tTgeyhYLIsJ2Fpg0kkS3SFFc/PWHX5LeQgi', 'ADMIN', null, 'org-2'),
('manager@entreprise.com', '$2a$10$qPsgOcTi0N5tD4KfiwqF0.sXbnyWs20ClmRMZnYqTMs.P3erZMhTu', 'MANAGER', null, 'org-2'),
-- Employes (password: employe123)
('luc.raharison@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-1', 'org-2'),
('soa.razafindrakoto@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-2', 'org-2'),
('aina.ravelonanahary@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-3', 'org-2'),
('fidy.randriamanantsoa@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-4', 'org-2'),
('haja.rakotomalala@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-5', 'org-2'),
('vola.rasoarimanana@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-6', 'org-2'),
('tiana.andrianaivomanana@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-7', 'org-2'),
('mamy.rajaonarivelo@entreprise.com', '$2a$10$HD24D/i2dInt.Us/jAj8JeRNYjV7i9TG108eLucc.0EP1zz1PR6bW', 'EMPLOYEE', 'ent-8', 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Exigences (avec site_id et organisation_id)
-- =====================
-- Company - Centre-Ville
INSERT INTO exigence (id, libelle, heure_debut, heure_fin, role, nombre_requis, site_id, organisation_id) VALUES
('exi-1', 'Caisse journee', 8, 18, 'Caissier', 2, 'site-centre', 'org-1'),
('exi-2', 'Vente matin', 9, 13, 'Vendeur', 2, 'site-centre', 'org-1'),
('exi-3', 'Vente apres-midi', 13, 18, 'Vendeur', 2, 'site-centre', 'org-1'),
('exi-4', 'Securite', 7, 19, 'Agent de securite', 1, 'site-centre', 'org-1'),
('exi-5', 'Accueil Centre', 8, 18, 'Accueil', 1, 'site-centre', 'org-1'),
-- Company - Banlieue Sud
('exi-6', 'Caisse Banlieue', 9, 17, 'Caissier', 1, 'site-banlieue', 'org-1'),
('exi-7', 'Vente Banlieue', 9, 17, 'Vendeur', 1, 'site-banlieue', 'org-1'),
('exi-8', 'Accueil Banlieue', 9, 17, 'Accueil', 1, 'site-banlieue', 'org-1'),
-- Company - Zone Industrielle
('exi-9', 'Manutention matin', 6, 12, 'Manutentionnaire', 2, 'site-zi', 'org-1'),
('exi-10', 'Magasin', 6, 14, 'Magasinier', 1, 'site-zi', 'org-1'),
('exi-11', 'Supervision', 7, 16, 'Responsable rayon', 1, 'site-zi', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise - Siege
INSERT INTO exigence (id, libelle, heure_debut, heure_fin, role, nombre_requis, site_id, organisation_id) VALUES
('exi-e1', 'Caisse Siege', 8, 17, 'Caissier', 2, 'site-siege', 'org-2'),
('exi-e2', 'Vente Siege matin', 9, 13, 'Vendeur', 1, 'site-siege', 'org-2'),
('exi-e3', 'Vente Siege apres-midi', 13, 18, 'Vendeur', 1, 'site-siege', 'org-2'),
('exi-e4', 'Securite Siege', 7, 19, 'Agent de securite', 1, 'site-siege', 'org-2'),
('exi-e5', 'Accueil Siege', 8, 17, 'Accueil', 1, 'site-siege', 'org-2'),
('exi-e6', 'Supervision Siege', 8, 17, 'Responsable rayon', 1, 'site-siege', 'org-2'),
('exi-e7', 'Magasin Siege', 7, 15, 'Magasinier', 1, 'site-siege', 'org-2') ON CONFLICT DO NOTHING;

INSERT INTO exigence_jours (exigence_id, jour) VALUES
-- Company: Lundi-Vendredi
('exi-1', 1), ('exi-1', 2), ('exi-1', 3), ('exi-1', 4), ('exi-1', 5),
('exi-2', 1), ('exi-2', 2), ('exi-2', 3), ('exi-2', 4), ('exi-2', 5),
('exi-3', 1), ('exi-3', 2), ('exi-3', 3), ('exi-3', 4), ('exi-3', 5),
('exi-4', 1), ('exi-4', 2), ('exi-4', 3), ('exi-4', 4), ('exi-4', 5),
('exi-5', 1), ('exi-5', 2), ('exi-5', 3), ('exi-5', 4), ('exi-5', 5),
('exi-6', 1), ('exi-6', 2), ('exi-6', 3), ('exi-6', 4), ('exi-6', 5),
('exi-7', 1), ('exi-7', 2), ('exi-7', 3), ('exi-7', 4), ('exi-7', 5),
('exi-8', 1), ('exi-8', 2), ('exi-8', 3), ('exi-8', 4), ('exi-8', 5),
('exi-9', 1), ('exi-9', 2), ('exi-9', 3), ('exi-9', 4), ('exi-9', 5),
('exi-10', 1), ('exi-10', 2), ('exi-10', 3), ('exi-10', 4), ('exi-10', 5),
('exi-11', 1), ('exi-11', 2), ('exi-11', 3), ('exi-11', 4), ('exi-11', 5),
-- Entreprise: Lundi-Vendredi
('exi-e1', 1), ('exi-e1', 2), ('exi-e1', 3), ('exi-e1', 4), ('exi-e1', 5),
('exi-e2', 1), ('exi-e2', 2), ('exi-e2', 3), ('exi-e2', 4), ('exi-e2', 5),
('exi-e3', 1), ('exi-e3', 2), ('exi-e3', 3), ('exi-e3', 4), ('exi-e3', 5),
('exi-e4', 1), ('exi-e4', 2), ('exi-e4', 3), ('exi-e4', 4), ('exi-e4', 5),
('exi-e5', 1), ('exi-e5', 2), ('exi-e5', 3), ('exi-e5', 4), ('exi-e5', 5),
('exi-e6', 1), ('exi-e6', 2), ('exi-e6', 3), ('exi-e6', 4), ('exi-e6', 5),
('exi-e7', 1), ('exi-e7', 2), ('exi-e7', 3), ('exi-e7', 4), ('exi-e7', 5) ON CONFLICT DO NOTHING;

-- =====================
-- Parametres (un par site, avec organisation_id)
-- =====================
INSERT INTO parametres (heure_debut, heure_fin, premier_jour, duree_min_affectation, site_id, organisation_id, taille_police, planning_vue, planning_granularite) VALUES
(6, 22, 1, 1.0, 'site-centre', 'org-1', 'petit', 'horaire', 1.0),
(6, 22, 1, 1.0, 'site-banlieue', 'org-1', 'petit', 'horaire', 1.0),
(6, 22, 1, 1.0, 'site-zi', 'org-1', 'petit', 'horaire', 1.0),
(6, 22, 1, 1.0, 'site-siege', 'org-2', 'petit', 'horaire', 1.0) ON CONFLICT DO NOTHING;

INSERT INTO parametres_jours_actifs (parametres_id, jour) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5),
(3, 1), (3, 2), (3, 3), (3, 4), (3, 5),
(4, 1), (4, 2), (4, 3), (4, 4), (4, 5) ON CONFLICT DO NOTHING;

-- =====================
-- Types de conge (avec organisation_id)
-- =====================
-- Company (org-1)
INSERT INTO type_conge (id, nom, categorie, unite, couleur, mode_quota, quota_illimite, autoriser_negatif, organisation_id) VALUES
('tc-1', 'Conge paye', 'paye', 'jours', '#4CAF50', 'annuel', false, false, 'org-1'),
('tc-2', 'Conge sans solde', 'non_paye', 'jours', '#FF9800', 'illimite', true, false, 'org-1'),
('tc-3', 'Arret maladie', 'paye', 'jours', '#F44336', 'illimite', true, false, 'org-1'),
('tc-4', 'Conge maternite', 'paye', 'jours', '#E91E63', 'evenement', false, false, 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2)
INSERT INTO type_conge (id, nom, categorie, unite, couleur, mode_quota, quota_illimite, autoriser_negatif, organisation_id) VALUES
('tc-e1', 'Conge paye', 'paye', 'jours', '#4CAF50', 'annuel', false, false, 'org-2'),
('tc-e2', 'Conge sans solde', 'non_paye', 'jours', '#FF9800', 'illimite', true, false, 'org-2'),
('tc-e3', 'Arret maladie', 'paye', 'jours', '#F44336', 'illimite', true, false, 'org-2'),
('tc-e4', 'Conge maternite', 'paye', 'jours', '#E91E63', 'evenement', false, false, 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Jours feries
-- =====================
-- Company (org-1): jours feries quebecois
INSERT INTO jour_ferie (id, nom, date, recurrent, organisation_id) VALUES
('jf-1', 'Jour de l''An', '2026-01-01', true, 'org-1'),
('jf-2', 'Vendredi saint', '2026-04-03', false, 'org-1'),
('jf-3', 'Lundi de Paques', '2026-04-06', false, 'org-1'),
('jf-4', 'Journee nationale des Patriotes', '2026-05-18', false, 'org-1'),
('jf-5', 'Fete nationale du Quebec', '2026-06-24', true, 'org-1'),
('jf-6', 'Fete du Canada', '2026-07-01', true, 'org-1'),
('jf-7', 'Fete du Travail', '2026-09-07', false, 'org-1'),
('jf-8', 'Action de graces', '2026-10-12', false, 'org-1'),
('jf-9', 'Noel', '2026-12-25', true, 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2): jours feries malgaches
INSERT INTO jour_ferie (id, nom, date, recurrent, organisation_id) VALUES
('jf-e1', 'Nouvel An', '2026-01-01', true, 'org-2'),
('jf-e2', 'Journee des Martyrs', '2026-03-29', true, 'org-2'),
('jf-e3', 'Fete du Travail', '2026-05-01', true, 'org-2'),
('jf-e4', 'Fete de l''Independance', '2026-06-26', true, 'org-2'),
('jf-e5', 'Assomption', '2026-08-15', true, 'org-2'),
('jf-e6', 'Toussaint', '2026-11-01', true, 'org-2'),
('jf-e7', 'Noel', '2026-12-25', true, 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Banques de conge (conge paye: 20 jours/an)
-- =====================
-- Company (org-1)
INSERT INTO banque_conge (id, employe_id, type_conge_id, quota, utilise, en_attente, date_debut, date_fin, organisation_id) VALUES
('bc-1', 'comp-1', 'tc-1', 20, 3, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-2', 'comp-2', 'tc-1', 20, 5, 2, '2026-01-01', '2026-12-31', 'org-1'),
('bc-3', 'comp-3', 'tc-1', 20, 0, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-4', 'comp-4', 'tc-1', 20, 1, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-5', 'comp-5', 'tc-1', 20, 2, 1, '2026-01-01', '2026-12-31', 'org-1'),
('bc-6', 'comp-6', 'tc-1', 20, 0, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-7', 'comp-7', 'tc-1', 20, 4, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-8', 'comp-8', 'tc-1', 20, 0, 3, '2026-01-01', '2026-12-31', 'org-1'),
('bc-9', 'comp-9', 'tc-1', 20, 6, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-10', 'comp-10', 'tc-1', 20, 1, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-11', 'comp-11', 'tc-1', 20, 0, 0, '2026-01-01', '2026-12-31', 'org-1'),
('bc-12', 'comp-12', 'tc-1', 20, 2, 0, '2026-01-01', '2026-12-31', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2)
INSERT INTO banque_conge (id, employe_id, type_conge_id, quota, utilise, en_attente, date_debut, date_fin, organisation_id) VALUES
('bc-e1', 'ent-1', 'tc-e1', 20, 2, 0, '2026-01-01', '2026-12-31', 'org-2'),
('bc-e2', 'ent-2', 'tc-e1', 20, 4, 1, '2026-01-01', '2026-12-31', 'org-2'),
('bc-e3', 'ent-3', 'tc-e1', 20, 0, 0, '2026-01-01', '2026-12-31', 'org-2'),
('bc-e4', 'ent-4', 'tc-e1', 20, 1, 0, '2026-01-01', '2026-12-31', 'org-2'),
('bc-e5', 'ent-5', 'tc-e1', 20, 3, 2, '2026-01-01', '2026-12-31', 'org-2'),
('bc-e6', 'ent-6', 'tc-e1', 20, 0, 0, '2026-01-01', '2026-12-31', 'org-2'),
('bc-e7', 'ent-7', 'tc-e1', 20, 5, 0, '2026-01-01', '2026-12-31', 'org-2'),
('bc-e8', 'ent-8', 'tc-e1', 20, 0, 1, '2026-01-01', '2026-12-31', 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Demandes de conge de test
-- =====================
-- Company (org-1)
INSERT INTO demande_conge (id, employe_id, type_conge_id, date_debut, date_fin, heure_debut, heure_fin, duree, statut, motif, organisation_id) VALUES
('dc-1', 'comp-2', 'tc-1', '2026-03-30', '2026-04-01', null, null, 2, 'en_attente', 'Affaire familiale', 'org-1'),
('dc-2', 'comp-9', 'tc-3', '2026-03-19', '2026-03-20', null, null, 2, 'approuve', 'Certificat medical fourni', 'org-1'),
('dc-3', 'comp-7', 'tc-1', '2026-03-30', '2026-03-30', null, null, 1, 'refuse', 'Rendez-vous personnel', 'org-1'),
('dc-4', 'comp-1', 'tc-1', '2026-04-13', '2026-04-14', null, null, 2, 'en_attente', 'Affaires personnelles', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise (org-2)
INSERT INTO demande_conge (id, employe_id, type_conge_id, date_debut, date_fin, heure_debut, heure_fin, duree, statut, motif, organisation_id) VALUES
('dc-e1', 'ent-2', 'tc-e1', '2026-03-30', '2026-03-31', null, null, 2, 'en_attente', 'Raharaha ara-pianakaviana', 'org-2'),
('dc-e2', 'ent-5', 'tc-e3', '2026-03-19', '2026-03-20', null, null, 2, 'approuve', 'Taratasy ara-pahasalamana', 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Creneaux pre-assignes pour semaine 2026-W13 (23-28 mars)
-- =====================

-- Company - Centre-Ville: Lundi a Vendredi
INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id) VALUES
-- Lundi (jour 1)
('ca-1', 'comp-1', 1, 8, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-2', 'comp-3', 1, 8, 17, '2026-W13', 'site-centre', 'org-1'),
('ca-3', 'comp-2', 1, 9, 13, '2026-W13', 'site-centre', 'org-1'),
('ca-4', 'comp-11', 1, 13, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-5', 'comp-4', 1, 7, 19, '2026-W13', 'site-centre', 'org-1'),
-- Mardi (jour 2)
('ca-6', 'comp-1', 2, 8, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-7', 'comp-3', 2, 8, 17, '2026-W13', 'site-centre', 'org-1'),
('ca-8', 'comp-2', 2, 9, 13, '2026-W13', 'site-centre', 'org-1'),
('ca-9', 'comp-11', 2, 13, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-10', 'comp-4', 2, 7, 19, '2026-W13', 'site-centre', 'org-1'),
-- Mercredi (jour 3)
('ca-11', 'comp-1', 3, 8, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-12', 'comp-3', 3, 8, 17, '2026-W13', 'site-centre', 'org-1'),
('ca-13', 'comp-2', 3, 9, 13, '2026-W13', 'site-centre', 'org-1'),
('ca-14', 'comp-11', 3, 13, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-15', 'comp-4', 3, 7, 19, '2026-W13', 'site-centre', 'org-1'),
-- Jeudi (jour 4)
('ca-16', 'comp-1', 4, 8, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-17', 'comp-3', 4, 8, 17, '2026-W13', 'site-centre', 'org-1'),
('ca-18', 'comp-2', 4, 9, 13, '2026-W13', 'site-centre', 'org-1'),
('ca-19', 'comp-11', 4, 13, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-20', 'comp-4', 4, 7, 19, '2026-W13', 'site-centre', 'org-1'),
-- Vendredi (jour 5)
('ca-21', 'comp-1', 5, 8, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-22', 'comp-3', 5, 8, 17, '2026-W13', 'site-centre', 'org-1'),
('ca-23', 'comp-2', 5, 9, 13, '2026-W13', 'site-centre', 'org-1'),
('ca-24', 'comp-11', 5, 13, 18, '2026-W13', 'site-centre', 'org-1'),
('ca-25', 'comp-4', 5, 7, 19, '2026-W13', 'site-centre', 'org-1') ON CONFLICT DO NOTHING;

-- Company - Banlieue Sud: Lundi a Vendredi
INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id) VALUES
('ca-30', 'comp-6', 1, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-31', 'comp-5', 1, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-32', 'comp-7', 1, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-33', 'comp-6', 2, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-34', 'comp-5', 2, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-35', 'comp-7', 2, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-36', 'comp-6', 3, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-37', 'comp-5', 3, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-38', 'comp-7', 3, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-39', 'comp-6', 4, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-40', 'comp-5', 4, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-41', 'comp-7', 4, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-42', 'comp-6', 5, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-43', 'comp-5', 5, 9, 17, '2026-W13', 'site-banlieue', 'org-1'),
('ca-44', 'comp-7', 5, 9, 17, '2026-W13', 'site-banlieue', 'org-1') ON CONFLICT DO NOTHING;

-- Company - Zone Industrielle: Lundi a Vendredi
INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id) VALUES
('ca-50', 'comp-9', 1, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-51', 'comp-12', 1, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-52', 'comp-8', 1, 6, 14, '2026-W13', 'site-zi', 'org-1'),
('ca-53', 'comp-10', 1, 7, 16, '2026-W13', 'site-zi', 'org-1'),
('ca-54', 'comp-9', 2, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-55', 'comp-12', 2, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-56', 'comp-8', 2, 6, 14, '2026-W13', 'site-zi', 'org-1'),
('ca-57', 'comp-10', 2, 7, 16, '2026-W13', 'site-zi', 'org-1'),
('ca-58', 'comp-9', 3, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-59', 'comp-12', 3, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-60', 'comp-8', 3, 6, 14, '2026-W13', 'site-zi', 'org-1'),
('ca-61', 'comp-10', 3, 7, 16, '2026-W13', 'site-zi', 'org-1'),
('ca-62', 'comp-9', 4, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-63', 'comp-12', 4, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-64', 'comp-8', 4, 6, 14, '2026-W13', 'site-zi', 'org-1'),
('ca-65', 'comp-10', 4, 7, 16, '2026-W13', 'site-zi', 'org-1'),
('ca-66', 'comp-9', 5, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-67', 'comp-12', 5, 6, 12, '2026-W13', 'site-zi', 'org-1'),
('ca-68', 'comp-8', 5, 6, 14, '2026-W13', 'site-zi', 'org-1'),
('ca-69', 'comp-10', 5, 7, 16, '2026-W13', 'site-zi', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise - Siege: Lundi a Vendredi
INSERT INTO creneau_assigne (id, employe_id, jour, heure_debut, heure_fin, semaine, site_id, organisation_id) VALUES
-- Lundi (jour 1)
('ca-e1', 'ent-1', 1, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e2', 'ent-8', 1, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e3', 'ent-2', 1, 9, 13, '2026-W13', 'site-siege', 'org-2'),
('ca-e4', 'ent-5', 1, 13, 18, '2026-W13', 'site-siege', 'org-2'),
('ca-e5', 'ent-4', 1, 7, 19, '2026-W13', 'site-siege', 'org-2'),
('ca-e6', 'ent-6', 1, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e7', 'ent-3', 1, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e8', 'ent-7', 1, 7, 15, '2026-W13', 'site-siege', 'org-2'),
-- Mardi (jour 2)
('ca-e9', 'ent-1', 2, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e10', 'ent-8', 2, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e11', 'ent-2', 2, 9, 13, '2026-W13', 'site-siege', 'org-2'),
('ca-e12', 'ent-5', 2, 13, 18, '2026-W13', 'site-siege', 'org-2'),
('ca-e13', 'ent-4', 2, 7, 19, '2026-W13', 'site-siege', 'org-2'),
('ca-e14', 'ent-6', 2, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e15', 'ent-3', 2, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e16', 'ent-7', 2, 7, 15, '2026-W13', 'site-siege', 'org-2'),
-- Mercredi (jour 3)
('ca-e17', 'ent-1', 3, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e18', 'ent-8', 3, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e19', 'ent-2', 3, 9, 13, '2026-W13', 'site-siege', 'org-2'),
('ca-e20', 'ent-5', 3, 13, 18, '2026-W13', 'site-siege', 'org-2'),
('ca-e21', 'ent-4', 3, 7, 19, '2026-W13', 'site-siege', 'org-2'),
('ca-e22', 'ent-6', 3, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e23', 'ent-3', 3, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e24', 'ent-7', 3, 7, 15, '2026-W13', 'site-siege', 'org-2'),
-- Jeudi (jour 4)
('ca-e25', 'ent-1', 4, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e26', 'ent-8', 4, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e27', 'ent-2', 4, 9, 13, '2026-W13', 'site-siege', 'org-2'),
('ca-e28', 'ent-5', 4, 13, 18, '2026-W13', 'site-siege', 'org-2'),
('ca-e29', 'ent-4', 4, 7, 19, '2026-W13', 'site-siege', 'org-2'),
('ca-e30', 'ent-6', 4, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e31', 'ent-3', 4, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e32', 'ent-7', 4, 7, 15, '2026-W13', 'site-siege', 'org-2'),
-- Vendredi (jour 5)
('ca-e33', 'ent-1', 5, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e34', 'ent-8', 5, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e35', 'ent-2', 5, 9, 13, '2026-W13', 'site-siege', 'org-2'),
('ca-e36', 'ent-5', 5, 13, 18, '2026-W13', 'site-siege', 'org-2'),
('ca-e37', 'ent-4', 5, 7, 19, '2026-W13', 'site-siege', 'org-2'),
('ca-e38', 'ent-6', 5, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e39', 'ent-3', 5, 8, 17, '2026-W13', 'site-siege', 'org-2'),
('ca-e40', 'ent-7', 5, 7, 15, '2026-W13', 'site-siege', 'org-2') ON CONFLICT DO NOTHING;

-- =====================
-- Pointages de test (20-21 mars 2026)
-- =====================
-- Company - Centre-Ville (vendredi 20 mars)
INSERT INTO pointage (id, employe_id, type, horodatage, methode, statut, anomalie, site_id, organisation_id) VALUES
('pt-1', 'comp-1', 'entree', '2026-03-20 07:52:00', 'pin', 'valide', null, 'site-centre', 'org-1'),
('pt-2', 'comp-1', 'sortie', '2026-03-20 18:00:00', 'pin', 'valide', null, 'site-centre', 'org-1'),
('pt-3', 'comp-2', 'entree', '2026-03-20 09:05:00', 'qr', 'valide', null, 'site-centre', 'org-1'),
('pt-4', 'comp-2', 'sortie', '2026-03-20 13:00:00', 'qr', 'valide', null, 'site-centre', 'org-1'),
('pt-5', 'comp-3', 'entree', '2026-03-20 08:00:00', 'web', 'valide', null, 'site-centre', 'org-1'),
('pt-6', 'comp-3', 'sortie', '2026-03-20 17:02:00', 'web', 'valide', null, 'site-centre', 'org-1'),
('pt-7', 'comp-4', 'entree', '2026-03-20 06:55:00', 'pin', 'valide', null, 'site-centre', 'org-1'),
('pt-8', 'comp-4', 'sortie', '2026-03-20 19:05:00', 'pin', 'valide', null, 'site-centre', 'org-1'),
-- Company - Banlieue (vendredi 20 mars)
('pt-9', 'comp-6', 'entree', '2026-03-20 08:55:00', 'pin', 'valide', null, 'site-banlieue', 'org-1'),
('pt-10', 'comp-6', 'sortie', '2026-03-20 17:00:00', 'pin', 'valide', null, 'site-banlieue', 'org-1'),
('pt-11', 'comp-5', 'entree', '2026-03-20 09:10:00', 'pin', 'anomalie', 'Retard 10min', 'site-banlieue', 'org-1'),
('pt-12', 'comp-5', 'sortie', '2026-03-20 17:05:00', 'pin', 'valide', null, 'site-banlieue', 'org-1'),
-- Company - Zone Industrielle (vendredi 20 mars)
('pt-13', 'comp-9', 'entree', '2026-03-20 05:58:00', 'pin', 'valide', null, 'site-zi', 'org-1'),
('pt-14', 'comp-9', 'sortie', '2026-03-20 12:02:00', 'pin', 'valide', null, 'site-zi', 'org-1'),
('pt-15', 'comp-8', 'entree', '2026-03-20 06:05:00', 'pin', 'valide', null, 'site-zi', 'org-1'),
('pt-16', 'comp-8', 'sortie', '2026-03-20 14:00:00', 'pin', 'valide', null, 'site-zi', 'org-1') ON CONFLICT DO NOTHING;

-- Entreprise - Siege (vendredi 20 mars)
INSERT INTO pointage (id, employe_id, type, horodatage, methode, statut, anomalie, site_id, organisation_id) VALUES
('pt-e1', 'ent-1', 'entree', '2026-03-20 07:55:00', 'pin', 'valide', null, 'site-siege', 'org-2'),
('pt-e2', 'ent-1', 'sortie', '2026-03-20 17:02:00', 'pin', 'valide', null, 'site-siege', 'org-2'),
('pt-e3', 'ent-2', 'entree', '2026-03-20 09:00:00', 'qr', 'valide', null, 'site-siege', 'org-2'),
('pt-e4', 'ent-2', 'sortie', '2026-03-20 13:05:00', 'qr', 'valide', null, 'site-siege', 'org-2'),
('pt-e5', 'ent-4', 'entree', '2026-03-20 06:50:00', 'pin', 'valide', null, 'site-siege', 'org-2'),
('pt-e6', 'ent-4', 'sortie', '2026-03-20 19:10:00', 'pin', 'valide', null, 'site-siege', 'org-2'),
('pt-e7', 'ent-7', 'entree', '2026-03-20 07:15:00', 'pin', 'anomalie', 'Retard 15min', 'site-siege', 'org-2'),
('pt-e8', 'ent-7', 'sortie', '2026-03-20 15:00:00', 'pin', 'valide', null, 'site-siege', 'org-2') ON CONFLICT DO NOTHING;

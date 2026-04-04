-- V16: Pause/Break management
-- Adds: pause table, pause-related columns on parametres, collection tables

-- =============================================
-- 1. Pause entity table
-- =============================================
CREATE TABLE pause (
    id VARCHAR(36) PRIMARY KEY,
    employe_id VARCHAR(36) NOT NULL,
    site_id VARCHAR(36),
    organisation_id VARCHAR(255) NOT NULL,

    debut TIMESTAMP WITH TIME ZONE NOT NULL,
    fin TIMESTAMP WITH TIME ZONE,
    duree_minutes INTEGER,

    type VARCHAR(30) NOT NULL,        -- REPAS, PAUSE, AUTO_DEDUCTION
    source VARCHAR(30) NOT NULL,      -- DETECTION_AUTO, REGLE, MANUEL
    statut VARCHAR(30) NOT NULL,      -- DETECTE, CONFIRME, CONTESTE, ANNULE
    payee BOOLEAN NOT NULL DEFAULT FALSE,

    pointage_sortie_id VARCHAR(36),
    pointage_entree_id VARCHAR(36),

    confirme_par_id VARCHAR(36),
    confirme_at TIMESTAMP WITH TIME ZONE,
    motif_contestation TEXT,

    CONSTRAINT fk_pause_org FOREIGN KEY (organisation_id) REFERENCES organisation(id)
);

CREATE INDEX idx_pause_employe_org ON pause (employe_id, organisation_id);
CREATE INDEX idx_pause_debut ON pause (debut);
CREATE INDEX idx_pause_site ON pause (site_id);

-- =============================================
-- 2. Parametres: labor law columns (from entity but missing migration)
-- =============================================
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS repos_min_entre_shifts DOUBLE PRECISION DEFAULT 0;
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS repos_hebdo_min DOUBLE PRECISION DEFAULT 0;
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS max_jours_consecutifs INTEGER DEFAULT 0;

-- =============================================
-- 3. Parametres: pause fixe collective (Layer 1)
-- =============================================
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS pause_fixe_heure_debut DOUBLE PRECISION;
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS pause_fixe_heure_fin DOUBLE PRECISION;

CREATE TABLE IF NOT EXISTS parametres_pause_fixe_jours (
    parametres_id BIGINT NOT NULL REFERENCES parametres(id) ON DELETE CASCADE,
    jour INTEGER NOT NULL
);

-- =============================================
-- 4. Parametres: pause rules (Layer 2)
-- =============================================
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS pause_avancee BOOLEAN DEFAULT FALSE;
-- Simple mode fields:
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS pause_seuil_heures DOUBLE PRECISION DEFAULT 0;
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS pause_duree_minutes INTEGER DEFAULT 0;
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS pause_payee BOOLEAN DEFAULT FALSE;

-- Advanced mode: tiered rules collection table
CREATE TABLE IF NOT EXISTS parametres_regles_pause (
    parametres_id BIGINT NOT NULL REFERENCES parametres(id) ON DELETE CASCADE,
    seuil_min_heures DOUBLE PRECISION NOT NULL,
    seuil_max_heures DOUBLE PRECISION,
    type VARCHAR(30) NOT NULL,
    duree_minutes INTEGER NOT NULL,
    payee BOOLEAN NOT NULL DEFAULT FALSE,
    divisible BOOLEAN NOT NULL DEFAULT FALSE,
    fraction_min_minutes INTEGER,
    fenetre_debut DOUBLE PRECISION,
    fenetre_fin DOUBLE PRECISION,
    ordre INTEGER NOT NULL DEFAULT 0
);

-- =============================================
-- 5. Parametres: detection window (Layer 3)
-- =============================================
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS fenetre_pause_min_minutes INTEGER DEFAULT 15;
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS fenetre_pause_max_minutes INTEGER DEFAULT 90;
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS pause_renoncement_autorise BOOLEAN DEFAULT FALSE;

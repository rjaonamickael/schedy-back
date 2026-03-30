-- ============================================================
-- V8__absence_imprevue.sql
-- Feature "Plan B" : absences imprevues + remplacement
-- ============================================================

CREATE TABLE IF NOT EXISTS absence_imprevue (
    id                VARCHAR(255)             NOT NULL,
    employe_id        VARCHAR(255)             NOT NULL,
    date_absence      DATE                     NOT NULL,
    motif             VARCHAR(255)             NOT NULL,
    message_employe   VARCHAR(500),
    signale_par       VARCHAR(255)             NOT NULL,
    initiateur        VARCHAR(20)              NOT NULL,
    date_signalement  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    statut            VARCHAR(20)              NOT NULL DEFAULT 'SIGNALEE',
    valide_par_email  VARCHAR(255),
    date_validation   TIMESTAMP WITH TIME ZONE,
    note_refus        VARCHAR(500),
    note_manager      VARCHAR(500),
    site_id           VARCHAR(255),
    organisation_id   VARCHAR(255)             NOT NULL,
    CONSTRAINT pk_absence_imprevue PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS absence_creneau_impacte (
    absence_id  VARCHAR(255) NOT NULL,
    creneau_id  VARCHAR(255) NOT NULL,
    CONSTRAINT fk_absence_creneau FOREIGN KEY (absence_id)
        REFERENCES absence_imprevue(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_absence_employe_org
    ON absence_imprevue (employe_id, organisation_id);

CREATE INDEX IF NOT EXISTS idx_absence_statut_org
    ON absence_imprevue (statut, organisation_id);

CREATE INDEX IF NOT EXISTS idx_absence_date_org
    ON absence_imprevue (date_absence, organisation_id);

-- Ajout du seuil configurable absence vs conge dans parametres
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS seuil_absence_vs_conge_heures INTEGER DEFAULT 48;

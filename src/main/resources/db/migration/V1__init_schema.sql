-- ============================================================
-- V1__init_schema.sql  -  Schedy initial schema (PostgreSQL)
-- ============================================================

CREATE TABLE IF NOT EXISTS organisation (
    id        VARCHAR(255) NOT NULL,
    nom       VARCHAR(255) NOT NULL,
    domaine   VARCHAR(255),
    adresse   VARCHAR(255),
    telephone VARCHAR(255),
    CONSTRAINT pk_organisation PRIMARY KEY (id),
    CONSTRAINT uq_organisation_nom UNIQUE (nom)
);

CREATE TABLE IF NOT EXISTS site (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    adresse         VARCHAR(255),
    telephone       VARCHAR(255),
    organisation_id VARCHAR(255) NOT NULL,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_site PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_site_org ON site (organisation_id);

CREATE TABLE IF NOT EXISTS role (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    importance      INTEGER NOT NULL,
    couleur         VARCHAR(255),
    organisation_id VARCHAR(255),
    CONSTRAINT pk_role PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_role_org ON role (organisation_id);

CREATE TABLE IF NOT EXISTS employe (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    role            VARCHAR(255),
    telephone       VARCHAR(255),
    email           VARCHAR(255),
    date_naissance  DATE,
    date_embauche   DATE,
    pin             VARCHAR(255),
    organisation_id VARCHAR(255),
    CONSTRAINT pk_employe PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_employe_org ON employe (organisation_id);
CREATE INDEX IF NOT EXISTS idx_employe_pin ON employe (pin);

CREATE TABLE IF NOT EXISTS employe_disponibilites (
    employe_id VARCHAR(255) NOT NULL,
    jour       INTEGER NOT NULL,
    heure_debut FLOAT8 NOT NULL,
    heure_fin   FLOAT8 NOT NULL,
    CONSTRAINT fk_disp_employe FOREIGN KEY (employe_id) REFERENCES employe (id)
);

CREATE TABLE IF NOT EXISTS employe_sites (
    employe_id VARCHAR(255) NOT NULL,
    site_id    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_empsite_employe FOREIGN KEY (employe_id) REFERENCES employe (id)
);

CREATE TABLE IF NOT EXISTS app_user (
    id              BIGSERIAL NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    role            VARCHAR(255) NOT NULL,
    employe_id      VARCHAR(255),
    organisation_id VARCHAR(255),
    refresh_token   VARCHAR(255),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);
CREATE INDEX IF NOT EXISTS idx_user_org ON app_user (organisation_id);

CREATE TABLE IF NOT EXISTS exigence (
    id              VARCHAR(255) NOT NULL,
    libelle         VARCHAR(255) NOT NULL,
    heure_debut     FLOAT8 NOT NULL,
    heure_fin       FLOAT8 NOT NULL,
    role            VARCHAR(255),
    nombre_requis   INTEGER NOT NULL,
    site_id         VARCHAR(255) NOT NULL,
    organisation_id VARCHAR(255),
    CONSTRAINT pk_exigence PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_exigence_org ON exigence (organisation_id);
CREATE INDEX IF NOT EXISTS idx_exigence_site ON exigence (site_id);

CREATE TABLE IF NOT EXISTS exigence_jours (
    exigence_id VARCHAR(255) NOT NULL,
    jour        INTEGER NOT NULL,
    CONSTRAINT fk_jour_exigence FOREIGN KEY (exigence_id) REFERENCES exigence (id)
);

CREATE TABLE IF NOT EXISTS creneau_assigne (
    id              VARCHAR(255) NOT NULL,
    employe_id      VARCHAR(255) NOT NULL,
    jour            INTEGER NOT NULL,
    heure_debut     FLOAT8 NOT NULL,
    heure_fin       FLOAT8 NOT NULL,
    semaine         VARCHAR(255) NOT NULL,
    site_id         VARCHAR(255) NOT NULL,
    organisation_id VARCHAR(255),
    CONSTRAINT pk_creneau_assigne PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_creneau_semaine_org ON creneau_assigne (semaine, organisation_id);
CREATE INDEX IF NOT EXISTS idx_creneau_employe_org ON creneau_assigne (employe_id, organisation_id);

CREATE TABLE IF NOT EXISTS pointage (
    id              VARCHAR(255) NOT NULL,
    employe_id      VARCHAR(255) NOT NULL,
    type            VARCHAR(255) NOT NULL,
    horodatage      TIMESTAMP NOT NULL,
    methode         VARCHAR(255) NOT NULL,
    statut          VARCHAR(255) NOT NULL DEFAULT 'valide',
    anomalie        VARCHAR(255),
    site_id         VARCHAR(255),
    organisation_id VARCHAR(255),
    CONSTRAINT pk_pointage PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pointage_employe_org ON pointage (employe_id, organisation_id);
CREATE INDEX IF NOT EXISTS idx_pointage_horodatage ON pointage (horodatage);
CREATE INDEX IF NOT EXISTS idx_pointage_site_org ON pointage (site_id, organisation_id);

CREATE TABLE IF NOT EXISTS pointage_code (
    id              VARCHAR(255) NOT NULL,
    site_id         VARCHAR(255) NOT NULL,
    code            VARCHAR(255) NOT NULL,
    pin             VARCHAR(255) NOT NULL,
    frequence       VARCHAR(255) NOT NULL,
    valid_from      TIMESTAMP NOT NULL,
    valid_to        TIMESTAMP NOT NULL,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    organisation_id VARCHAR(255),
    CONSTRAINT pk_pointage_code PRIMARY KEY (id),
    CONSTRAINT uq_pointage_code_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS parametres (
    id                     BIGSERIAL NOT NULL,
    heure_debut            INTEGER NOT NULL DEFAULT 6,
    heure_fin              INTEGER NOT NULL DEFAULT 22,
    premier_jour           INTEGER NOT NULL DEFAULT 1,
    duree_min_affectation  FLOAT8 NOT NULL DEFAULT 1.0,
    heures_max_semaine     FLOAT8 NOT NULL DEFAULT 48.0,
    site_id                VARCHAR(255),
    organisation_id        VARCHAR(255),
    taille_police          VARCHAR(255),
    planning_vue           VARCHAR(255),
    planning_granularite   FLOAT8 NOT NULL DEFAULT 1.0,
    CONSTRAINT pk_parametres PRIMARY KEY (id),
    CONSTRAINT uq_parametres_site UNIQUE (site_id)
);

CREATE TABLE IF NOT EXISTS parametres_jours_actifs (
    parametres_id BIGINT NOT NULL,
    jour          INTEGER NOT NULL,
    CONSTRAINT fk_joursactifs_parametres FOREIGN KEY (parametres_id) REFERENCES parametres (id)
);

CREATE TABLE IF NOT EXISTS parametres_regles_affectation (
    parametres_id BIGINT NOT NULL,
    regle         VARCHAR(255) NOT NULL,
    CONSTRAINT fk_regles_parametres FOREIGN KEY (parametres_id) REFERENCES parametres (id)
);

CREATE TABLE IF NOT EXISTS type_conge (
    id                VARCHAR(255) NOT NULL,
    nom               VARCHAR(255) NOT NULL,
    categorie         VARCHAR(255) NOT NULL,
    unite             VARCHAR(255) NOT NULL,
    organisation_id   VARCHAR(255),
    couleur           VARCHAR(255),
    mode_quota        VARCHAR(255),
    quota_illimite    BOOLEAN NOT NULL DEFAULT FALSE,
    autoriser_negatif BOOLEAN NOT NULL DEFAULT FALSE,
    accrual_montant   FLOAT8,
    accrual_frequence VARCHAR(255),
    report_max        FLOAT8,
    report_duree      INTEGER,
    CONSTRAINT pk_type_conge PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_type_conge_org ON type_conge (organisation_id);

CREATE TABLE IF NOT EXISTS demande_conge (
    id               VARCHAR(255) NOT NULL,
    employe_id       VARCHAR(255) NOT NULL,
    type_conge_id    VARCHAR(255) NOT NULL,
    date_debut       DATE NOT NULL,
    date_fin         DATE NOT NULL,
    heure_debut      FLOAT8,
    heure_fin        FLOAT8,
    duree            FLOAT8 NOT NULL,
    statut           VARCHAR(255) NOT NULL DEFAULT 'en_attente',
    motif            VARCHAR(255),
    note_approbation VARCHAR(255),
    organisation_id  VARCHAR(255),
    CONSTRAINT pk_demande_conge PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_demande_employe_org ON demande_conge (employe_id, organisation_id);
CREATE INDEX IF NOT EXISTS idx_demande_statut ON demande_conge (statut);

CREATE TABLE IF NOT EXISTS banque_conge (
    id              VARCHAR(255) NOT NULL,
    employe_id      VARCHAR(255) NOT NULL,
    type_conge_id   VARCHAR(255) NOT NULL,
    quota           FLOAT8,
    utilise         FLOAT8 NOT NULL DEFAULT 0,
    en_attente      FLOAT8 NOT NULL DEFAULT 0,
    date_debut      DATE,
    date_fin        DATE,
    organisation_id VARCHAR(255),
    CONSTRAINT pk_banque_conge PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_banque_employe_type ON banque_conge (employe_id, type_conge_id);
ALTER TABLE banque_conge ADD CONSTRAINT IF NOT EXISTS uq_banque_employe_type_org UNIQUE (employe_id, type_conge_id, organisation_id);

CREATE TABLE IF NOT EXISTS jour_ferie (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    date            DATE NOT NULL,
    recurrent       BOOLEAN NOT NULL DEFAULT FALSE,
    site_id         VARCHAR(255),
    organisation_id VARCHAR(255),
    CONSTRAINT pk_jour_ferie PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_jour_ferie_org ON jour_ferie (organisation_id);

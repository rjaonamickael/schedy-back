-- ============================================================
-- V1__init_schema.sql  —  Schedy COMPLETE schema (PostgreSQL)
-- Consolidates V1 through V7 into a single authoritative file.
--
-- To apply on a fresh database:
--   DROP SCHEMA public CASCADE; CREATE SCHEMA public;
--   Then let Flyway run.
--
-- Uses TIMESTAMPTZ everywhere (not TIMESTAMP).
-- Uses IF NOT EXISTS / idempotent DO blocks for safety.
-- ============================================================

-- pgcrypto: needed for BCrypt in V2 seed data
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- ORGANISATION
-- ============================================================
CREATE TABLE IF NOT EXISTS organisation (
    id         VARCHAR(255) NOT NULL,
    nom        VARCHAR(255) NOT NULL,
    domaine    VARCHAR(255),
    adresse    VARCHAR(255),
    telephone  VARCHAR(255),
    pays       VARCHAR(3),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    status     VARCHAR(50)              DEFAULT 'ACTIVE',
    notes      TEXT,
    CONSTRAINT pk_organisation PRIMARY KEY (id),
    CONSTRAINT uq_organisation_nom UNIQUE (nom)
);

-- ============================================================
-- SITE
-- ============================================================
CREATE TABLE IF NOT EXISTS site (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    adresse         VARCHAR(255),
    ville           VARCHAR(255),
    code_postal     VARCHAR(20),
    telephone       VARCHAR(255),
    organisation_id VARCHAR(255) NOT NULL,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_site PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_site_org ON site (organisation_id);

-- ============================================================
-- ROLE
-- ============================================================
CREATE TABLE IF NOT EXISTS role (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    importance      INTEGER NOT NULL,
    couleur         VARCHAR(255),
    organisation_id VARCHAR(255),
    CONSTRAINT pk_role PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_role_org ON role (organisation_id);

-- ============================================================
-- EMPLOYE
-- Includes: pin (BCrypt), pin_hash (SHA-256), pin_clair (raw)
-- ============================================================
CREATE TABLE IF NOT EXISTS employe (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    role            VARCHAR(255),
    telephone       VARCHAR(255),
    email           VARCHAR(255),
    date_naissance  DATE,
    date_embauche   DATE,
    pin             VARCHAR(255),          -- BCrypt hash
    pin_hash        VARCHAR(255),          -- SHA-256 hex for fast lookup
    pin_clair       VARCHAR(255),          -- Raw PIN shown in employee dashboard
    organisation_id VARCHAR(255),
    CONSTRAINT pk_employe PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_employe_org      ON employe (organisation_id);
CREATE INDEX IF NOT EXISTS idx_employe_pin      ON employe (pin);
CREATE INDEX IF NOT EXISTS idx_employe_pin_hash ON employe (pin_hash, organisation_id);

-- ============================================================
-- EMPLOYE_DISPONIBILITES  (ElementCollection — EAGER)
-- ============================================================
CREATE TABLE IF NOT EXISTS employe_disponibilites (
    employe_id  VARCHAR(255) NOT NULL,
    jour        INTEGER      NOT NULL,
    heure_debut FLOAT8       NOT NULL,
    heure_fin   FLOAT8       NOT NULL,
    CONSTRAINT fk_disp_employe FOREIGN KEY (employe_id) REFERENCES employe (id)
);

-- ============================================================
-- EMPLOYE_SITES  (ElementCollection — EAGER)
-- ============================================================
CREATE TABLE IF NOT EXISTS employe_sites (
    employe_id VARCHAR(255) NOT NULL,
    site_id    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_empsite_employe FOREIGN KEY (employe_id) REFERENCES employe (id)
);

-- ============================================================
-- APP_USER
-- Includes: nom (profile display name)
-- ============================================================
CREATE TABLE IF NOT EXISTS app_user (
    id              BIGSERIAL    NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    role            VARCHAR(255) NOT NULL,
    nom             VARCHAR(255),
    employe_id      VARCHAR(255),
    organisation_id VARCHAR(255),
    refresh_token   VARCHAR(255),
    CONSTRAINT pk_app_user  PRIMARY KEY (id),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);
CREATE INDEX IF NOT EXISTS idx_user_org ON app_user (organisation_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_refresh_token
    ON app_user (refresh_token)
    WHERE refresh_token IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_employe_id
    ON app_user (employe_id)
    WHERE employe_id IS NOT NULL;

-- ============================================================
-- EXIGENCE
-- ============================================================
CREATE TABLE IF NOT EXISTS exigence (
    id              VARCHAR(255) NOT NULL,
    libelle         VARCHAR(255) NOT NULL,
    heure_debut     FLOAT8       NOT NULL,
    heure_fin       FLOAT8       NOT NULL,
    role            VARCHAR(255),
    nombre_requis   INTEGER      NOT NULL,
    site_id         VARCHAR(255) NOT NULL,
    organisation_id VARCHAR(255),
    CONSTRAINT pk_exigence PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_exigence_org  ON exigence (organisation_id);
CREATE INDEX IF NOT EXISTS idx_exigence_site ON exigence (site_id);

-- ============================================================
-- EXIGENCE_JOURS  (ElementCollection — EAGER)
-- ============================================================
CREATE TABLE IF NOT EXISTS exigence_jours (
    exigence_id VARCHAR(255) NOT NULL,
    jour        INTEGER      NOT NULL,
    CONSTRAINT fk_jour_exigence FOREIGN KEY (exigence_id) REFERENCES exigence (id)
);

-- ============================================================
-- CRENEAU_ASSIGNE
-- ============================================================
CREATE TABLE IF NOT EXISTS creneau_assigne (
    id              VARCHAR(255) NOT NULL,
    employe_id      VARCHAR(255) NOT NULL,
    jour            INTEGER      NOT NULL,
    heure_debut     FLOAT8       NOT NULL,
    heure_fin       FLOAT8       NOT NULL,
    semaine         VARCHAR(255) NOT NULL,
    site_id         VARCHAR(255) NOT NULL,
    organisation_id VARCHAR(255),
    CONSTRAINT pk_creneau_assigne PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_creneau_semaine_org ON creneau_assigne (semaine, organisation_id);
CREATE INDEX IF NOT EXISTS idx_creneau_employe_org ON creneau_assigne (employe_id, organisation_id);

-- ============================================================
-- POINTAGE  (TIMESTAMPTZ — multi-country timezone support)
-- ============================================================
CREATE TABLE IF NOT EXISTS pointage (
    id              VARCHAR(255)             NOT NULL,
    employe_id      VARCHAR(255)             NOT NULL,
    type            VARCHAR(255)             NOT NULL,
    horodatage      TIMESTAMP WITH TIME ZONE NOT NULL,
    methode         VARCHAR(255)             NOT NULL,
    statut          VARCHAR(255)             NOT NULL DEFAULT 'valide',
    anomalie        VARCHAR(255),
    site_id         VARCHAR(255),
    organisation_id VARCHAR(255),
    CONSTRAINT pk_pointage PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_pointage_employe_org ON pointage (employe_id, organisation_id);
CREATE INDEX IF NOT EXISTS idx_pointage_horodatage  ON pointage (horodatage);
CREATE INDEX IF NOT EXISTS idx_pointage_site_org    ON pointage (site_id, organisation_id);

-- ============================================================
-- POINTAGE_CODE  (TIMESTAMPTZ — multi-country timezone support)
-- Includes: pin_hash (SHA-256 for fast lookup)
-- ============================================================
CREATE TABLE IF NOT EXISTS pointage_code (
    id              VARCHAR(255)             NOT NULL,
    site_id         VARCHAR(255)             NOT NULL,
    code            VARCHAR(255)             NOT NULL,
    pin             VARCHAR(255)             NOT NULL,
    pin_hash        VARCHAR(64),
    rotation_valeur INTEGER                  NOT NULL DEFAULT 1,
    rotation_unite  VARCHAR(50)              NOT NULL DEFAULT 'JOURS',
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_to        TIMESTAMP WITH TIME ZONE NOT NULL,
    actif           BOOLEAN                  NOT NULL DEFAULT TRUE,
    organisation_id VARCHAR(255),
    CONSTRAINT pk_pointage_code    PRIMARY KEY (id),
    CONSTRAINT uq_pointage_code_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_pointage_code_site_actif     ON pointage_code (site_id, actif);
CREATE INDEX IF NOT EXISTS idx_pointage_code_pin_hash_actif ON pointage_code (pin_hash, actif);
-- Enforce at most one active code per site (partial unique index — only active rows are indexed)
CREATE UNIQUE INDEX IF NOT EXISTS uq_pointage_code_site_actif ON pointage_code (site_id) WHERE actif = TRUE;

-- ============================================================
-- PARAMETRES
-- Includes: heures_max_semaine (DEFAULT 48)
-- ============================================================
CREATE TABLE IF NOT EXISTS parametres (
    id                    BIGSERIAL    NOT NULL,
    heure_debut           INTEGER      NOT NULL DEFAULT 6,
    heure_fin             INTEGER      NOT NULL DEFAULT 22,
    premier_jour          INTEGER      NOT NULL DEFAULT 1,
    duree_min_affectation FLOAT8       NOT NULL DEFAULT 1.0,
    heures_max_semaine    FLOAT8       NOT NULL DEFAULT 48.0,
    site_id               VARCHAR(255),
    organisation_id       VARCHAR(255),
    taille_police         VARCHAR(255),
    planning_vue          VARCHAR(255),
    planning_granularite  FLOAT8       NOT NULL DEFAULT 1.0,
    CONSTRAINT pk_parametres    PRIMARY KEY (id),
    CONSTRAINT uq_parametres_site UNIQUE (site_id)
);

-- ============================================================
-- PARAMETRES_JOURS_ACTIFS  (ElementCollection — EAGER)
-- ============================================================
CREATE TABLE IF NOT EXISTS parametres_jours_actifs (
    parametres_id BIGINT  NOT NULL,
    jour          INTEGER NOT NULL,
    CONSTRAINT fk_joursactifs_parametres FOREIGN KEY (parametres_id) REFERENCES parametres (id)
);

-- ============================================================
-- PARAMETRES_REGLES_AFFECTATION  (ElementCollection — EAGER)
-- ============================================================
CREATE TABLE IF NOT EXISTS parametres_regles_affectation (
    parametres_id BIGINT       NOT NULL,
    regle         VARCHAR(255) NOT NULL,
    CONSTRAINT fk_regles_parametres FOREIGN KEY (parametres_id) REFERENCES parametres (id)
);

-- ============================================================
-- TYPE_CONGE
-- ============================================================
CREATE TABLE IF NOT EXISTS type_conge (
    id                VARCHAR(255) NOT NULL,
    nom               VARCHAR(255) NOT NULL,
    categorie         VARCHAR(255) NOT NULL,
    unite             VARCHAR(255) NOT NULL,
    organisation_id   VARCHAR(255),
    couleur           VARCHAR(255),
    mode_quota        VARCHAR(255),
    quota_illimite    BOOLEAN      NOT NULL DEFAULT FALSE,
    autoriser_negatif BOOLEAN      NOT NULL DEFAULT FALSE,
    accrual_montant   FLOAT8,
    accrual_frequence VARCHAR(255),
    report_max        FLOAT8,
    report_duree      INTEGER,
    CONSTRAINT pk_type_conge PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_type_conge_org ON type_conge (organisation_id);

-- ============================================================
-- DEMANDE_CONGE
-- ============================================================
CREATE TABLE IF NOT EXISTS demande_conge (
    id               VARCHAR(255) NOT NULL,
    employe_id       VARCHAR(255) NOT NULL,
    type_conge_id    VARCHAR(255) NOT NULL,
    date_debut       DATE         NOT NULL,
    date_fin         DATE         NOT NULL,
    heure_debut      FLOAT8,
    heure_fin        FLOAT8,
    duree            FLOAT8       NOT NULL,
    statut           VARCHAR(255) NOT NULL DEFAULT 'en_attente',
    motif            VARCHAR(255),
    note_approbation VARCHAR(255),
    organisation_id  VARCHAR(255),
    CONSTRAINT pk_demande_conge PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_demande_employe_org ON demande_conge (employe_id, organisation_id);
CREATE INDEX IF NOT EXISTS idx_demande_statut      ON demande_conge (statut);
CREATE INDEX IF NOT EXISTS idx_demande_type_conge  ON demande_conge (type_conge_id);

-- ============================================================
-- BANQUE_CONGE
-- Includes: version (optimistic locking)
-- ============================================================
CREATE TABLE IF NOT EXISTS banque_conge (
    id              VARCHAR(255) NOT NULL,
    employe_id      VARCHAR(255) NOT NULL,
    type_conge_id   VARCHAR(255) NOT NULL,
    quota           FLOAT8,
    utilise         FLOAT8       NOT NULL DEFAULT 0,
    en_attente      FLOAT8       NOT NULL DEFAULT 0,
    date_debut      DATE,
    date_fin        DATE,
    organisation_id VARCHAR(255),
    version         BIGINT,
    CONSTRAINT pk_banque_conge PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_banque_employe_type ON banque_conge (employe_id, type_conge_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_banque_employe_type_org ON banque_conge (employe_id, type_conge_id, organisation_id);

-- ============================================================
-- JOUR_FERIE
-- ============================================================
CREATE TABLE IF NOT EXISTS jour_ferie (
    id              VARCHAR(255) NOT NULL,
    nom             VARCHAR(255) NOT NULL,
    date            DATE         NOT NULL,
    recurrent       BOOLEAN      NOT NULL DEFAULT FALSE,
    site_id         VARCHAR(255),
    organisation_id VARCHAR(255),
    CONSTRAINT pk_jour_ferie PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_jour_ferie_org ON jour_ferie (organisation_id);

-- ============================================================
-- SUBSCRIPTION  (V7 — one per organisation)
-- ============================================================
CREATE TABLE IF NOT EXISTS subscription (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    organisation_id VARCHAR(255) NOT NULL UNIQUE REFERENCES organisation (id),
    plan_tier       VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    status          VARCHAR(50)  NOT NULL DEFAULT 'TRIAL',
    trial_ends_at   TIMESTAMP WITH TIME ZONE,
    max_employees   INTEGER      NOT NULL DEFAULT 15,
    max_sites       INTEGER      NOT NULL DEFAULT 1,
    promo_code_id   VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_subscription_org    ON subscription (organisation_id);
CREATE INDEX IF NOT EXISTS idx_subscription_status ON subscription (status);

-- ============================================================
-- PROMO_CODE  (V7)
-- ============================================================
CREATE TABLE IF NOT EXISTS promo_code (
    id               VARCHAR(255) NOT NULL PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    description      VARCHAR(500),
    discount_percent INTEGER,
    discount_months  INTEGER,
    plan_override    VARCHAR(50),
    max_uses         INTEGER,
    current_uses     INTEGER      NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    valid_from       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    valid_to         TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_promo_code_code ON promo_code (code);

-- ============================================================
-- FEATURE_FLAG  (V7 — per organisation)
-- ============================================================
CREATE TABLE IF NOT EXISTS feature_flag (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    organisation_id VARCHAR(255) NOT NULL REFERENCES organisation (id),
    feature_key     VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_flag_org_key UNIQUE (organisation_id, feature_key)
);
CREATE INDEX IF NOT EXISTS idx_feature_flag_org ON feature_flag (organisation_id);

-- ============================================================
-- PLATFORM_ANNOUNCEMENT  (V7 — global platform messages)
-- ============================================================
CREATE TABLE IF NOT EXISTS platform_announcement (
    id         VARCHAR(255)             NOT NULL PRIMARY KEY,
    title      VARCHAR(255)             NOT NULL,
    body       TEXT                     NOT NULL,
    severity   VARCHAR(50)              NOT NULL DEFAULT 'INFO',
    active     BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at TIMESTAMP WITH TIME ZONE
);

-- ============================================================
-- IMPERSONATION_LOG  (V7 — superadmin audit trail)
-- ============================================================
CREATE TABLE IF NOT EXISTS impersonation_log (
    id               VARCHAR(255) NOT NULL PRIMARY KEY,
    superadmin_email VARCHAR(255) NOT NULL,
    target_org_id    VARCHAR(255) NOT NULL,
    target_org_name  VARCHAR(255) NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ended_at         TIMESTAMP WITH TIME ZONE,
    ip_address       VARCHAR(45),
    reason           VARCHAR(500)
);

-- ============================================================
-- FOREIGN KEY CONSTRAINTS  (added last so all tables exist)
-- All use DO blocks with duplicate_object guard.
-- ============================================================

-- pointage.employe_id → employe.id  (CASCADE: safe to remove pointages with employee)
DO $$ BEGIN
    ALTER TABLE pointage
        ADD CONSTRAINT fk_pointage_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- creneau_assigne.employe_id → employe.id  (CASCADE)
DO $$ BEGIN
    ALTER TABLE creneau_assigne
        ADD CONSTRAINT fk_creneau_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- creneau_assigne.site_id → site.id  (CASCADE)
DO $$ BEGIN
    ALTER TABLE creneau_assigne
        ADD CONSTRAINT fk_creneau_site
        FOREIGN KEY (site_id) REFERENCES site (id)
        ON DELETE CASCADE;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- demande_conge.employe_id → employe.id  (RESTRICT)
DO $$ BEGIN
    ALTER TABLE demande_conge
        ADD CONSTRAINT fk_demande_conge_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- demande_conge.type_conge_id → type_conge.id  (RESTRICT)
DO $$ BEGIN
    ALTER TABLE demande_conge
        ADD CONSTRAINT fk_demande_conge_type
        FOREIGN KEY (type_conge_id) REFERENCES type_conge (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- banque_conge.employe_id → employe.id  (RESTRICT)
DO $$ BEGIN
    ALTER TABLE banque_conge
        ADD CONSTRAINT fk_banque_conge_employe
        FOREIGN KEY (employe_id) REFERENCES employe (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- banque_conge.type_conge_id → type_conge.id  (RESTRICT)
DO $$ BEGIN
    ALTER TABLE banque_conge
        ADD CONSTRAINT fk_banque_conge_type
        FOREIGN KEY (type_conge_id) REFERENCES type_conge (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- exigence.site_id → site.id  (RESTRICT)
DO $$ BEGIN
    ALTER TABLE exigence
        ADD CONSTRAINT fk_exigence_site
        FOREIGN KEY (site_id) REFERENCES site (id)
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

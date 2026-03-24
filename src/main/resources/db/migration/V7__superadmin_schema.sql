-- V7: SUPERADMIN schema — subscriptions, promo codes, feature flags, announcements, impersonation audit

-- Subscription per organisation
CREATE TABLE subscription (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    organisation_id VARCHAR(255) NOT NULL UNIQUE REFERENCES organisation(id),
    plan_tier       VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    status          VARCHAR(50)  NOT NULL DEFAULT 'TRIAL',
    trial_ends_at   TIMESTAMP WITH TIME ZONE,
    max_employees   INTEGER      NOT NULL DEFAULT 15,
    max_sites       INTEGER      NOT NULL DEFAULT 1,
    promo_code_id   VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscription_org    ON subscription(organisation_id);
CREATE INDEX idx_subscription_status ON subscription(status);

-- Promo codes
CREATE TABLE promo_code (
    id               VARCHAR(255) NOT NULL PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    description      VARCHAR(500),
    discount_percent INTEGER,
    discount_months  INTEGER,
    plan_override    VARCHAR(50),
    max_uses         INTEGER,
    current_uses     INTEGER NOT NULL DEFAULT 0,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    valid_to         TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_promo_code_code ON promo_code(code);

-- Feature flags per org
CREATE TABLE feature_flag (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    organisation_id VARCHAR(255) NOT NULL REFERENCES organisation(id),
    feature_key     VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_flag_org_key UNIQUE (organisation_id, feature_key)
);
CREATE INDEX idx_feature_flag_org ON feature_flag(organisation_id);

-- Platform announcements
CREATE TABLE platform_announcement (
    id         VARCHAR(255)  NOT NULL PRIMARY KEY,
    title      VARCHAR(255)  NOT NULL,
    body       TEXT          NOT NULL,
    severity   VARCHAR(50)   NOT NULL DEFAULT 'INFO',
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at TIMESTAMP WITH TIME ZONE
);

-- Impersonation audit log
CREATE TABLE impersonation_log (
    id                VARCHAR(255) NOT NULL PRIMARY KEY,
    superadmin_email  VARCHAR(255) NOT NULL,
    target_org_id     VARCHAR(255) NOT NULL,
    target_org_name   VARCHAR(255) NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ended_at          TIMESTAMP WITH TIME ZONE,
    ip_address        VARCHAR(45),
    reason            VARCHAR(500)
);

-- Add metadata columns to organisation (idempotent via DO block)
DO $$ BEGIN
    ALTER TABLE organisation ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT now();
    ALTER TABLE organisation ADD COLUMN IF NOT EXISTS status     VARCHAR(50)              DEFAULT 'ACTIVE';
    ALTER TABLE organisation ADD COLUMN IF NOT EXISTS notes      TEXT;
EXCEPTION WHEN duplicate_column THEN NULL;
END $$;

-- ============================================================
-- V15__plan_templates.sql
-- Dynamic plan template system.
-- Replaces hardcoded PlanTier enum logic with DB-managed plans.
-- ============================================================

CREATE TABLE IF NOT EXISTS plan_template (
    id            VARCHAR(255)             NOT NULL,
    code          VARCHAR(50)              NOT NULL,
    display_name  VARCHAR(100)             NOT NULL,
    description   VARCHAR(500),
    max_employees INTEGER                  NOT NULL DEFAULT 15,
    max_sites     INTEGER                  NOT NULL DEFAULT 1,
    price_monthly NUMERIC(10, 2),
    price_annual  NUMERIC(10, 2),
    trial_days    INTEGER                  NOT NULL DEFAULT 0,
    active        BOOLEAN                  NOT NULL DEFAULT TRUE,
    sort_order    INTEGER                  NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_plan_template  PRIMARY KEY (id),
    CONSTRAINT uq_plan_template_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_plan_template_code   ON plan_template (code);
CREATE INDEX IF NOT EXISTS idx_plan_template_active ON plan_template (active);

CREATE TABLE IF NOT EXISTS plan_template_feature (
    plan_template_id VARCHAR(255) NOT NULL,
    feature_key      VARCHAR(100) NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_plan_template_feature PRIMARY KEY (plan_template_id, feature_key),
    CONSTRAINT fk_ptf_template FOREIGN KEY (plan_template_id)
        REFERENCES plan_template (id) ON DELETE CASCADE
);

-- ============================================================
-- Seed: Essentials (FREE)
-- Up to 15 employees, 1 site, no AUTO_AFFECTATION
-- ============================================================
INSERT INTO plan_template (id, code, display_name, description, max_employees, max_sites,
                           price_monthly, price_annual, trial_days, active, sort_order)
VALUES (gen_random_uuid(), 'FREE', 'Essentials',
        'Gratuit, jusqu''à 15 employés. Idéal pour tester.',
        15, 1, NULL, NULL, 0, TRUE, 0);

INSERT INTO plan_template_feature (plan_template_id, feature_key, enabled)
SELECT pt.id, t.feature_key, t.enabled
FROM plan_template pt
CROSS JOIN (VALUES
    ('PLANNING',         TRUE),
    ('POINTAGE',         TRUE),
    ('CONGES',           TRUE),
    ('EXPORT',           TRUE),
    ('AUTO_AFFECTATION', FALSE)
) AS t(feature_key, enabled)
WHERE pt.code = 'FREE';

-- ============================================================
-- Seed: Pro
-- Up to 500 employees, unlimited sites, all features, 7-day trial
-- ============================================================
INSERT INTO plan_template (id, code, display_name, description, max_employees, max_sites,
                           price_monthly, price_annual, trial_days, active, sort_order)
VALUES (gen_random_uuid(), 'PRO', 'Pro',
        'Plan complet pour les équipes de 16 à 500. Plan B, multi-sites.',
        500, -1, 1.99, 1.49, 7, TRUE, 1);

INSERT INTO plan_template_feature (plan_template_id, feature_key, enabled)
SELECT pt.id, t.feature_key, t.enabled
FROM plan_template pt
CROSS JOIN (VALUES
    ('PLANNING',         TRUE),
    ('POINTAGE',         TRUE),
    ('CONGES',           TRUE),
    ('EXPORT',           TRUE),
    ('AUTO_AFFECTATION', TRUE)
) AS t(feature_key, enabled)
WHERE pt.code = 'PRO';

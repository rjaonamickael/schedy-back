-- ══════════════════════════════════════════════════════════════
-- V45 — Stripe billing integration + organisation profile fields
-- ──────────────────────────────────────────────────────────────
-- Adds:
--  1. organisation: legal_representative, contact_email, siret,
--                   stripe_customer_id (nullable, unique-when-set)
--  2. subscription: stripe_subscription_id, stripe_price_id,
--                   current_period_end, cancel_at_period_end,
--                   latest_invoice_status
--  3. plan_template: stripe_monthly_price_id, stripe_annual_price_id
--  4. stripe_event: webhook idempotency table (PK = Stripe event id)
--
-- Stripe is the source of truth for billing state; the columns added
-- here are a local cache so the UI never needs a roundtrip to Stripe
-- to render the subscription summary card.
-- ══════════════════════════════════════════════════════════════

-- ── 1. Organisation: profile + Stripe customer ──
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS legal_representative VARCHAR(255),
    ADD COLUMN IF NOT EXISTS contact_email        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS siret                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS stripe_customer_id   VARCHAR(255);

-- One Stripe Customer per org. NULL while the org has never started
-- checkout; populated lazily on first billing interaction.
CREATE UNIQUE INDEX IF NOT EXISTS uq_organisation_stripe_customer
    ON organisation (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

-- ── 2. Subscription: Stripe subscription mirror ──
ALTER TABLE subscription
    ADD COLUMN IF NOT EXISTS stripe_subscription_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_price_id        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS current_period_end     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancel_at_period_end   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS latest_invoice_status  VARCHAR(50);

-- A Stripe subscription id is globally unique. Partial unique index
-- so existing TRIAL rows (where the column is NULL) do not collide.
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_stripe_sub
    ON subscription (stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;

-- ── 3. PlanTemplate: Stripe price ids ──
ALTER TABLE plan_template
    ADD COLUMN IF NOT EXISTS stripe_monthly_price_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_annual_price_id  VARCHAR(255);

-- ── 4. Stripe event idempotency ──
-- The PRIMARY KEY on the Stripe event id is the race-condition shield:
-- two concurrent webhook deliveries with the same event.id will collide
-- on insert; the loser catches DataIntegrityViolationException and
-- returns 200 without re-processing.
CREATE TABLE IF NOT EXISTS stripe_event (
    id              VARCHAR(255) PRIMARY KEY,
    type            VARCHAR(100) NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL,
    processed_at    TIMESTAMPTZ,
    organisation_id VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_stripe_event_received_at
    ON stripe_event (received_at);

CREATE INDEX IF NOT EXISTS idx_stripe_event_org
    ON stripe_event (organisation_id)
    WHERE organisation_id IS NOT NULL;

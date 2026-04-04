-- V24 — Testimonials
-- Stores organisation testimonials submitted for public display on the landing page.
-- Lifecycle: PENDING (submitted) -> APPROVED (visible) | REJECTED (hidden).

CREATE TABLE IF NOT EXISTS testimonial (
    id               VARCHAR(255)              PRIMARY KEY,
    organisation_id  VARCHAR(255)              NOT NULL REFERENCES organisation(id),
    author_name      VARCHAR(100)              NOT NULL,
    author_role      VARCHAR(100)              NOT NULL,
    author_city      VARCHAR(100),
    quote            TEXT                      NOT NULL,
    stars            INT                       NOT NULL DEFAULT 5,
    language         VARCHAR(5)                NOT NULL DEFAULT 'fr',
    status           VARCHAR(20)               NOT NULL DEFAULT 'PENDING',
    display_order    INT                       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    reviewed_at      TIMESTAMP WITH TIME ZONE,
    reviewed_by      VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_testimonial_status ON testimonial (status);
CREATE INDEX IF NOT EXISTS idx_testimonial_org    ON testimonial (organisation_id);

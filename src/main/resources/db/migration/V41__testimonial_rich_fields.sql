-- V41 — Rich testimonial fields
--
-- Adds the fields needed for the new landing-page testimonial card:
--   * Social links (LinkedIn + company website)
--   * Sanitized logo URL (served from Cloudflare R2)
--   * Three optional structured text sections (probleme / solution / impact)
--     used by the guided submission form. Authors still fill `quote` as a
--     short one-line summary — the structured sections add context but are
--     not required so existing rows remain valid.
ALTER TABLE testimonial
    ADD COLUMN IF NOT EXISTS linkedin_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS website_url   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS logo_url      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS text_probleme TEXT,
    ADD COLUMN IF NOT EXISTS text_solution TEXT,
    ADD COLUMN IF NOT EXISTS text_impact   TEXT;

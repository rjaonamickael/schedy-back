-- ============================================================
-- V31__exigence_period_scoping.sql
-- Sprint 16 / Feature 1 : variable staffing needs by period.
--
-- An Exigence can now be period-scoped (e.g. "from 2026-12-15
-- to 2026-01-05, need 2 extra cooks and 1 extra dishwasher")
-- without rebuilding the base exigences.
--
-- Rules :
--   * dateDebut IS NULL -> base exigence, applies year-round
--   * dateDebut / dateFin non-null -> period override, applies only
--     when (lundi >= dateDebut AND lundi <= dateFin)
--   * priorite : higher wins when two exigences cover the same
--     (role, siteId, jour, heure) tuple. Base exigences stay at 0.
--     Period overrides use positive integers (1, 2, 3...).
-- ============================================================

ALTER TABLE exigence
    ADD COLUMN date_debut DATE NULL,
    ADD COLUMN date_fin   DATE NULL,
    ADD COLUMN priorite   INT  NOT NULL DEFAULT 0;

-- Partial index to speed up period-filtered queries.
-- Only period overrides pay the index cost; base exigences (NULL) are untouched.
CREATE INDEX idx_exigence_period
    ON exigence (organisation_id, site_id)
    WHERE date_debut IS NOT NULL;

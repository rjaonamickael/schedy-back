-- ============================================================
-- V21__add_missing_indexes_round3.sql
--
-- Adds 9 indexes across 7 tables identified in the DB audit
-- (issues DB-1, DB-3, DB-6, DB-8, DB-10).
--
-- Skipped (already exist):
--   idx_user_invitation_token      — created as UNIQUE in V3
--   idx_user_password_reset_token  — created as UNIQUE in V9
--
-- All statements use IF NOT EXISTS for safe re-execution.
-- Column names verified against V1, V15, V16.
-- ============================================================


-- ============================================================
-- DB-1: ElementCollection FK indexes (4 tables, from V1 + V16)
--
-- Hibernate @BatchSize(50) issues WHERE parametres_id = ANY(?)
-- predicates. Without these indexes every batched EAGER load
-- causes a sequential scan on the collection table.
-- ============================================================

-- parametres_pause_fixe_jours — created in V16 with no index
CREATE INDEX IF NOT EXISTS idx_parametres_pause_fixe_jours_pid
    ON parametres_pause_fixe_jours (parametres_id);

-- parametres_regles_pause — created in V16 with no index
CREATE INDEX IF NOT EXISTS idx_parametres_regles_pause_pid
    ON parametres_regles_pause (parametres_id);

-- parametres_regles_affectation — created in V1 with no index
CREATE INDEX IF NOT EXISTS idx_parametres_regles_aff_pid
    ON parametres_regles_affectation (parametres_id);

-- parametres_jours_actifs — created in V1 with no index
CREATE INDEX IF NOT EXISTS idx_parametres_jours_actifs_pid
    ON parametres_jours_actifs (parametres_id);


-- ============================================================
-- DB-3: plan_template_feature FK index (from V15)
--
-- Feature lookup for a given plan does a WHERE plan_template_id = ?
-- join against the PK. Without this index that join is a full scan
-- on the collection table for every plan hydration.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_plan_template_feature_ptid
    ON plan_template_feature (plan_template_id);


-- ============================================================
-- DB-6: organisation.status index
--
-- The superadmin dashboard runs 5 COUNT(*) queries grouping by
-- organisation status (ACTIVE / SUSPENDED / TRIAL etc.). A plain
-- index on status lets the planner use an index-only scan instead
-- of a full sequential scan on the organisation table.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_organisation_status
    ON organisation (status);


-- ============================================================
-- DB-8: Scheduler composite index on pointage_code (from V1)
--
-- The rotation scheduler queries active codes whose valid_to has
-- passed:  WHERE actif = TRUE AND valid_to < now()
-- The existing uq_pointage_code_site_actif partial unique index
-- covers site_id only for active rows. This partial index on
-- valid_to (restricted to active rows) gives the scheduler an
-- efficient range scan without touching expired/inactive codes.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_pointage_code_actif_valid_to
    ON pointage_code (valid_to)
    WHERE actif = TRUE;


-- ============================================================
-- DB-10: impersonation_log sort index (from V1)
--
-- The superadmin audit log is always fetched ORDER BY started_at DESC
-- with a LIMIT for pagination. Without an index on started_at every
-- page fetch performs a full sequential scan + sort. DESC ordering
-- stored in the index matches the query sort direction exactly,
-- allowing PostgreSQL to avoid a separate sort step.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_impersonation_log_started_at
    ON impersonation_log (started_at DESC);

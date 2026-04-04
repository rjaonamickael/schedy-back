-- ============================================================
-- V17__add_missing_indexes.sql
--
-- Adds 7 indexes covering frequently queried foreign keys and
-- filter columns that have no index coverage after V1-V16.
--
-- Impact per table:
--   employe_sites           — site_id FK scan (no index existed)
--   pause                   — org-scoped time-range queries
--   demande_conge           — org-scoped approval queue filter
--   parametres              — settings lookup by organisation
--   absence_creneau_impacte — FK cascade lookup on absence_id
--   pointage                — org-scoped time-range queries
--   employe_disponibilites  — FK batch-load by employe_id
--
-- All statements use IF NOT EXISTS for safe re-execution.
-- Composite index column order: most selective column first.
-- ============================================================

-- 1. employe_sites — unindexed site_id FK causes full-table scans
--    when resolving which employees belong to a site.
CREATE INDEX IF NOT EXISTS idx_employe_sites_site_id
    ON employe_sites (site_id);

-- 2. pause — org-scoped time-range queries (e.g. "all pauses for
--    org X between date A and date B"). organisation_id goes first
--    because it is the high-cardinality tenant discriminator;
--    debut is then used for range pruning.
--    Note: idx_pause_employe_org, idx_pause_debut, idx_pause_site
--    already exist from V16 — this composite covers org-level scans.
CREATE INDEX IF NOT EXISTS idx_pause_org_debut
    ON pause (organisation_id, debut);

-- 3. demande_conge — approval-queue queries filter on organisation_id
--    + statut (e.g. "all pending requests for org X").
--    idx_demande_statut (statut only) exists but does not support
--    org-scoped filtering efficiently without this composite.
CREATE INDEX IF NOT EXISTS idx_demande_conge_org_statut
    ON demande_conge (organisation_id, statut);

-- 4. parametres — settings are always loaded by organisation_id;
--    only a UNIQUE constraint on site_id existed, no org index.
CREATE INDEX IF NOT EXISTS idx_parametres_org
    ON parametres (organisation_id);

-- 5. absence_creneau_impacte — absence_id FK has no index; every
--    ON DELETE CASCADE or JOIN from absence_imprevue triggers a
--    sequential scan on this collection table.
CREATE INDEX IF NOT EXISTS idx_absence_creneau_absence_id
    ON absence_creneau_impacte (absence_id);

-- 6. pointage — org-scoped time-range report queries (e.g. "all
--    clock-ins for org X this week"). organisation_id first for
--    tenant isolation; horodatage for range pruning.
--    idx_pointage_horodatage (standalone) and idx_pointage_employe_org
--    already exist from V1 — this composite covers manager-level
--    dashboard queries that do not filter by a specific employee.
CREATE INDEX IF NOT EXISTS idx_pointage_org_horodatage
    ON pointage (organisation_id, horodatage);

-- 7. employe_disponibilites — employe_id FK has no index; Hibernate
--    @BatchSize(50) batched loads still issue a WHERE employe_id = ANY(?)
--    predicate that benefits from this index.
CREATE INDEX IF NOT EXISTS idx_employe_disponibilites_employe_id
    ON employe_disponibilites (employe_id);

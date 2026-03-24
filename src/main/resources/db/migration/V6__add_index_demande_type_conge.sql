-- B-19: Add index on demande_conge.type_conge_id for faster join/filter queries
-- This index benefits deleteByTypeCongeIdAndOrganisationId, findByTypeCongeIdAndStatut, etc.
CREATE INDEX IF NOT EXISTS idx_demande_type_conge ON demande_conge (type_conge_id);

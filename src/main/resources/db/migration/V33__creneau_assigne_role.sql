-- ============================================================
-- V33__creneau_assigne_role.sql
-- Sprint 16 / Feature 2 : creneau captures the role being filled.
--
-- A multi-role employee (e.g. "cuisinier" primary + "plongeur"
-- secondary) can now be assigned as a "plongeur" on Tuesday and
-- as a "cuisinier" on Thursday in the same week. Without this
-- column, the UI could not display which role the employee is
-- filling on a given creneau.
--
-- Default is NULL (legacy creneaux have no role context). New
-- creneaux populate this field from the exigence that triggered
-- the assignment (AutoAffectationService) or from an explicit
-- choice in modal-assignation (manual drag-drop).
--
-- The UNIQUE constraint from V28 (organisation_id, employeId,
-- semaine, jour, siteId, heureDebut, heureFin) is NOT extended
-- to include `role` : an employee cannot physically fill two
-- roles in the same time slot, so role is deterministically
-- implied by the slot.
-- ============================================================

ALTER TABLE creneau_assigne
    ADD COLUMN role VARCHAR(255) NULL;

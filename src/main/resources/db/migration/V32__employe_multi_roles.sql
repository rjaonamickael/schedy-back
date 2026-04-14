-- ============================================================
-- V32__employe_multi_roles.sql
-- Sprint 16 / Feature 2 : multi-role employees with hierarchy.
--
-- An employee can now hold multiple roles with explicit order :
--   roles[0] = role principal (primary, used for display, full
--              scoring weight in ReplacementService)
--   roles[1] = secondaire (secondary, partial scoring weight)
--   roles[2] = tertiaire, etc.
--
-- Data migration :
--   1. Create collection table employe_roles
--   2. Backfill current Employe.role as the primary role (index 0)
--   3. Drop the old employe.role column
--
-- The whole migration runs in a single Flyway transaction. If any
-- step fails, the entire schema change rolls back and employe.role
-- is preserved intact.
-- ============================================================

-- Step 1 : create the collection table with an explicit order column
CREATE TABLE employe_roles (
    employe_id  VARCHAR(255) NOT NULL,
    role_ordre  INT          NOT NULL,
    role        VARCHAR(255) NOT NULL,
    PRIMARY KEY (employe_id, role_ordre),
    CONSTRAINT fk_employe_roles_employe
        FOREIGN KEY (employe_id)
        REFERENCES employe(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_employe_roles_employe ON employe_roles (employe_id);
CREATE INDEX idx_employe_roles_role    ON employe_roles (role);

-- Step 2 : backfill — every existing employee with a non-blank role
--          gets that role as their primary (index 0).
INSERT INTO employe_roles (employe_id, role_ordre, role)
SELECT id, 0, role
FROM employe
WHERE role IS NOT NULL AND role <> '';

-- Step 3 : drop the old column.
--          The Java entity replaces `private String role` with
--          `private List<String> roles` via @ElementCollection
--          and @OrderColumn in the same deploy.
ALTER TABLE employe DROP COLUMN role;

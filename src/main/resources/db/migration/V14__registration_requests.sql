-- ============================================================
-- V14__registration_requests.sql
-- Public registration request system.
-- Businesses submit requests; SUPERADMIN approves or rejects.
-- ============================================================

CREATE TABLE IF NOT EXISTS registration_request (
    id                VARCHAR(255)             NOT NULL,
    organisation_name VARCHAR(255)             NOT NULL,
    contact_name      VARCHAR(255)             NOT NULL,
    contact_email     VARCHAR(255)             NOT NULL,
    contact_phone     VARCHAR(50),
    pays              VARCHAR(3),
    province          VARCHAR(10),
    adresse           VARCHAR(500),
    business_number   VARCHAR(50),
    provincial_id     VARCHAR(50),
    nif               VARCHAR(50),
    stat              VARCHAR(50),
    desired_plan      VARCHAR(20),
    employee_count    INTEGER,
    message           TEXT,
    status            VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    rejection_reason  TEXT,
    reviewed_at       TIMESTAMP WITH TIME ZONE,
    reviewed_by       VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_registration_request PRIMARY KEY (id),
    CONSTRAINT chk_reg_request_status  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_reg_request_status     ON registration_request (status);
CREATE INDEX IF NOT EXISTS idx_reg_request_created_at ON registration_request (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reg_request_email      ON registration_request (contact_email);

-- Prevent duplicate PENDING requests from same email
CREATE UNIQUE INDEX IF NOT EXISTS uq_reg_request_pending_email
    ON registration_request (contact_email)
    WHERE status = 'PENDING';

-- Restrict desired_plan to valid values
ALTER TABLE registration_request
    ADD CONSTRAINT chk_reg_request_plan CHECK (desired_plan IN ('FREE', 'STARTER', 'PRO', 'CUSTOM'));

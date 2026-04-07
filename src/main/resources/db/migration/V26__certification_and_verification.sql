-- Registration request: record that the applicant certified their information
ALTER TABLE registration_request ADD COLUMN certification_accepted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE registration_request ADD COLUMN certification_accepted_at TIMESTAMPTZ;

-- Organisation: verification status tracked by superadmin
ALTER TABLE organisation ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED';
ALTER TABLE organisation ADD COLUMN verified_by VARCHAR(255);
ALTER TABLE organisation ADD COLUMN verified_at TIMESTAMPTZ;
ALTER TABLE organisation ADD COLUMN verification_note TEXT;

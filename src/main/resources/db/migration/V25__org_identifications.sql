-- Add identification columns to organisation table
ALTER TABLE organisation ADD COLUMN province VARCHAR(10);
ALTER TABLE organisation ADD COLUMN business_number VARCHAR(50);
ALTER TABLE organisation ADD COLUMN provincial_id VARCHAR(50);
ALTER TABLE organisation ADD COLUMN nif VARCHAR(50);
ALTER TABLE organisation ADD COLUMN stat VARCHAR(50);

-- Backfill from approved registration requests
UPDATE organisation o
SET province        = r.province,
    business_number = r.business_number,
    provincial_id   = r.provincial_id,
    nif             = r.nif,
    stat            = r.stat
FROM registration_request r
WHERE r.organisation_name = o.nom
  AND r.status = 'APPROVED';

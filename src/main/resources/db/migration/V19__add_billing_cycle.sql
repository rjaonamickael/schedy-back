-- V19: add billing_cycle column to registration_request
-- Values: 'ANNUAL' | 'MONTHLY' | NULL (legacy rows)
ALTER TABLE registration_request ADD COLUMN IF NOT EXISTS billing_cycle VARCHAR(20);

-- V27: Rename plan tier FREE -> ESSENTIALS across all tables
-- This aligns DB values with commercial naming (Essentials / Pro)

-- 1. Update data
UPDATE subscription SET plan_tier = 'ESSENTIALS' WHERE plan_tier = 'FREE';
UPDATE registration_request SET desired_plan = 'ESSENTIALS' WHERE desired_plan = 'FREE';
UPDATE plan_template SET code = 'ESSENTIALS' WHERE code = 'FREE';

-- 2. Update default value on subscription.plan_tier
ALTER TABLE subscription ALTER COLUMN plan_tier SET DEFAULT 'ESSENTIALS';

-- 3. Replace CHECK constraint on registration_request.desired_plan
ALTER TABLE registration_request DROP CONSTRAINT IF EXISTS chk_reg_request_plan;
ALTER TABLE registration_request ADD CONSTRAINT chk_reg_request_plan
    CHECK (desired_plan IN ('ESSENTIALS', 'STARTER', 'PRO', 'CUSTOM'));

-- ============================================================
-- V28__update_pro_plan_prices.sql
-- Updates the PRO plan pricing to reflect the beta launch decision:
--   monthly: $1.99 → $2.99
--   annual : $1.49 → $2.49
-- Aligns fresh installs with the existing superadmin-managed state,
-- so that once migrated, landing + registration display the new
-- values via the public plan-templates endpoint.
-- ============================================================

UPDATE plan_template
SET price_monthly = 2.99,
    price_annual  = 2.49,
    updated_at    = now()
WHERE code = 'PRO';

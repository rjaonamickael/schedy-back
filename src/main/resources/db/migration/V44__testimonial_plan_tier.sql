-- V44 — Denormalize the org's subscription plan tier onto the testimonial row.
--
-- Why denormalize (stamp-at-submit) instead of joining at read time:
--   1. Preserves "plan at the moment of testimony" for historical accuracy.
--      An org that submits a testimonial on ESSENTIALS then upgrades to PRO
--      should keep the original badge — the quote is anchored to that tier.
--   2. Zero join on the public landing endpoint, which is rate-limited and
--      hit by anonymous traffic.
--   3. Matches the pattern already used for logo_url (captured once, served
--      without runtime dependencies).
--
-- Nullable so existing rows don't break the migration. TestimonialService.submit()
-- stamps the current subscription's tier going forward; the backfill below
-- applies the existing plan to any testimonials already in the table.

ALTER TABLE testimonial
    ADD COLUMN IF NOT EXISTS plan_tier VARCHAR(20);

-- Backfill existing rows by joining on the current subscription. For orgs
-- without a subscription row (edge case / stale data) we leave NULL; the
-- frontend falls back to hiding the badge in that case.
UPDATE testimonial t
   SET plan_tier = s.plan_tier
  FROM subscription s
 WHERE s.organisation_id = t.organisation_id
   AND t.plan_tier IS NULL;

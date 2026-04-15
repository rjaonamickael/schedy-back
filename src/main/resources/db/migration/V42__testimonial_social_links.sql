-- V42 — Extra social links on testimonial
--
-- Adds Facebook, Instagram and X (Twitter) profile URLs. All three are
-- optional and displayed as small icon-buttons next to the existing
-- LinkedIn + website icons on the public card.
--
-- The X column is named "twitter_url" so a future rebrand back doesn't
-- need another migration; the Java field/getter is "twitterUrl".
ALTER TABLE testimonial
    ADD COLUMN IF NOT EXISTS facebook_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS instagram_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS twitter_url   VARCHAR(500);

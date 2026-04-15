-- V43 — Optional catchy title above the testimonial quote.
--
-- Rendered as a bold heading on the public testimonial card, ahead of
-- the quote body. Optional — testimonials without a title still render
-- the quote alone, matching the v41 layout.
ALTER TABLE testimonial
    ADD COLUMN IF NOT EXISTS quote_title VARCHAR(200);

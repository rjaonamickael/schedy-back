-- V4: Add organisation_id to platform_announcement for org-scoped announcements.
-- NULL = global (visible to all organisations); non-null = visible only to the specified org.
ALTER TABLE platform_announcement ADD COLUMN IF NOT EXISTS organisation_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_announcement_org ON platform_announcement (organisation_id);

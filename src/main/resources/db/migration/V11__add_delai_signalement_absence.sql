ALTER TABLE parametres
    ADD COLUMN IF NOT EXISTS delai_signalement_absence_minutes INTEGER NOT NULL DEFAULT 60;

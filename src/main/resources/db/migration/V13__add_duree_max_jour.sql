-- Add max daily hours limit to parametres
ALTER TABLE parametres ADD COLUMN IF NOT EXISTS duree_max_jour DOUBLE PRECISION DEFAULT 10.0;

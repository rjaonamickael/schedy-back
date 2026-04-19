-- V48 : Refonte modele temoignages — logo/socials entreprise -> Organisation, photo auteur -> Testimonial snapshot
-- Rationale :
--   * Logo, website, LinkedIn entreprise deviennent des champs Organisation (config unique reutilisable ailleurs : factures, signatures email, kiosque).
--   * Facebook/Instagram/Twitter supprimes (< 5% usage B2B pro, validation Round 1 experts).
--   * author_photo_url ajoute sur Testimonial : snapshot de la photo utilisateur au moment du submit.
--   * 0 ligne testimonial en base (beta recente) -> drop-column safe, pas de data migration.
--   * Snapshot strategy : au submit, serveur copie Organisation.* + User.* vers Testimonial.*. Pas de jointure live.

-- 1) Organisation : ajout logo + reseaux sociaux entreprise (URLs publiques).
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS logo_url     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS website_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS linkedin_url VARCHAR(500);

-- 2) Testimonial : drop des 3 colonnes sociales supprimees.
ALTER TABLE testimonial
    DROP COLUMN IF EXISTS facebook_url,
    DROP COLUMN IF EXISTS instagram_url,
    DROP COLUMN IF EXISTS twitter_url;

-- 3) Testimonial : ajout des 2 snapshots nouveaux au submit
--    - author_photo_url : snapshot de User.photoUrl (photo personnelle auteur)
--    - organisation_linkedin_url : snapshot de Organisation.linkedinUrl (distinct
--      du champ linkedin_url existant qui, lui, porte le LinkedIn perso auteur
--      snapshote depuis User.linkedinUrl — cf Testimonial.java commentaire).
ALTER TABLE testimonial
    ADD COLUMN IF NOT EXISTS author_photo_url         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS organisation_linkedin_url VARCHAR(500);

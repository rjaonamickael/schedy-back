-- V49 : User profil personnel — photo + LinkedIn perso.
-- Utilise par le nouveau formulaire temoignage comme source de verite
-- (au submit, serveur copie app_user.photo_url et app_user.linkedin_url
-- dans Testimonial.author_photo_url et Testimonial.linkedin_url).

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS photo_url    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS linkedin_url VARCHAR(500);

-- V51 : Restauration des 3 liens sociaux entreprise (Facebook, Instagram, X/Twitter)
-- supprimes en V48 apres retour utilisateur : ces canaux sont utilises en B2B PME
-- QC/MG (restaurants, retail, services). Le pattern snapshot V48 est conserve :
-- Organisation porte la source de verite, Testimonial snapshot au submit.
-- (Numerotee V51 car V50 etait deja prise par la migration user_sessions.)

-- 1) Organisation : ajout des 3 colonnes source de verite.
ALTER TABLE organisation
    ADD COLUMN IF NOT EXISTS facebook_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS instagram_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS twitter_url   VARCHAR(500);

-- 2) Testimonial : ajout des 3 colonnes snapshot (re-copiees au submit/update
-- depuis Organisation.*). Independantes des colonnes dropped en V48 pour eviter
-- les corruptions de donnees historiques.
ALTER TABLE testimonial
    ADD COLUMN IF NOT EXISTS facebook_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS instagram_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS twitter_url   VARCHAR(500);

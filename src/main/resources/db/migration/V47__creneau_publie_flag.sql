-- V47 : flag publication des créneaux (workflow brouillon → publication).
--
-- Les créneaux nouvellement créés naissent en brouillon (publie=false) et
-- ne sont visibles que par les admins/managers. Ils deviennent visibles
-- aux employés uniquement via l'action explicite POST /creneaux/publier.
--
-- Rétrocompatibilité beta : tous les créneaux existants en base passent
-- à publie=true pour ne pas rompre la vue planning des beta testeurs.

ALTER TABLE creneau_assigne
    ADD COLUMN publie BOOLEAN NOT NULL DEFAULT false;

UPDATE creneau_assigne SET publie = true;

-- Index composite pour les deux accès chauds post-V47 :
--   a) filtre employé : (org, semaine, publie=true) → bloc final
--   b) action Publier  : (org, semaine, publie=false) → bloc initial
-- Un seul index couvre les deux via leading columns (organisation_id, semaine).
CREATE INDEX idx_creneau_publie_semaine_org
    ON creneau_assigne (organisation_id, semaine, publie);

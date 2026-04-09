-- ============================================================
-- V28 : Contrainte d'unicité sur creneau_assigne
-- ============================================================
-- Contexte : CreneauAssigne n'avait aucune contrainte d'unicité, ce qui
-- permettait de créer plusieurs lignes identiques pour le même employé
-- sur le même slot (même semaine/jour/heure/site). Ces doublons faisaient
-- diverger les compteurs "X/Y" des listes d'employés côté frontend et
-- corrompaient les KPIs de couverture.
--
-- Étapes :
--   1. Supprimer les doublons exacts existants (garde une ligne par tuple)
--   2. Ajouter la contrainte d'unicité
-- ============================================================

-- 1. Nettoyage des doublons exacts : on garde la ligne avec l'id minimum
--    pour chaque tuple (employe_id, semaine, jour, heure_debut, heure_fin,
--    site_id, organisation_id). Les doublons sont supprimés.
DELETE FROM creneau_assigne c1
USING creneau_assigne c2
WHERE c1.id > c2.id
  AND c1.employe_id      = c2.employe_id
  AND c1.semaine         = c2.semaine
  AND c1.jour            = c2.jour
  AND c1.heure_debut     = c2.heure_debut
  AND c1.heure_fin       = c2.heure_fin
  AND c1.site_id         = c2.site_id
  AND c1.organisation_id IS NOT DISTINCT FROM c2.organisation_id;

-- 2. Contrainte d'unicité. Empêche désormais toute insertion d'un créneau
--    strictement identique (même employé, même semaine/jour/heures, même
--    site dans la même organisation).
ALTER TABLE creneau_assigne
    ADD CONSTRAINT uk_creneau_assigne_employe_slot
    UNIQUE (organisation_id, employe_id, semaine, jour, site_id, heure_debut, heure_fin);

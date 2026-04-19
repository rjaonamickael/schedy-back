package com.schedy.repository;

import com.schedy.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, String> {
    Optional<Organisation> findByDomaine(String domaine);
    Optional<Organisation> findByNom(String nom);
    boolean existsByNom(String nom);
    /** Used by the admin self-service profile update to detect nom collisions
     *  while ignoring the org's own current row. */
    boolean existsByNomAndIdNot(String nom, String id);
    long countByStatus(String status);

    /** Used by RenouvellementCongesScheduler to find orgs whose renewal day matches today. */
    List<Organisation> findByDateRenouvellementConges(String dateRenouvellementConges);

    /** Used by the Stripe webhook handler to map a Stripe customer back to an org. */
    Optional<Organisation> findByStripeCustomerId(String stripeCustomerId);

    /** V48 — R2 GC guard : vrai si le logo est encore la valeur courante d'une orga. */
    boolean existsByLogoUrl(String logoUrl);
}

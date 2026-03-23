package com.schedy.repository;

import com.schedy.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, String> {
    List<Site> findByActifTrue();
    Optional<Site> findByNom(String nom);
    boolean existsByNom(String nom);
    boolean existsByNomAndOrganisationId(String nom, String organisationId);
    List<Site> findByOrganisationId(String organisationId);
    List<Site> findByOrganisationIdAndActifTrue(String organisationId);

    // Organisation-scoped queries
    Page<Site> findByOrganisationId(String organisationId, Pageable pageable);
    Optional<Site> findByIdAndOrganisationId(String id, String organisationId);
}

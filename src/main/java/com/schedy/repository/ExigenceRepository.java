package com.schedy.repository;

import com.schedy.entity.Exigence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExigenceRepository extends JpaRepository<Exigence, String> {
    List<Exigence> findByRole(String role);

    // Multi-site queries
    List<Exigence> findBySiteId(String siteId);
    List<Exigence> findByRoleAndSiteId(String role, String siteId);
    Page<Exigence> findBySiteId(String siteId, Pageable pageable);

    // Organisation-scoped queries
    Page<Exigence> findByOrganisationId(String organisationId, Pageable pageable);
    List<Exigence> findByOrganisationId(String organisationId);
    Optional<Exigence> findByIdAndOrganisationId(String id, String organisationId);
    Page<Exigence> findBySiteIdAndOrganisationId(String siteId, String organisationId, Pageable pageable);
    List<Exigence> findBySiteIdAndOrganisationId(String siteId, String organisationId);
    List<Exigence> findByRoleAndOrganisationId(String role, String organisationId);
    List<Exigence> findByRoleAndSiteIdAndOrganisationId(String role, String siteId, String organisationId);
    void deleteByOrganisationId(String organisationId);
}

package com.schedy.repository;

import com.schedy.entity.Employe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeRepository extends JpaRepository<Employe, String> {
    List<Employe> findByRole(String role);
    List<Employe> findByOrganisationId(String organisationId);

    // Multi-site queries
    @Query("SELECT e FROM Employe e JOIN e.siteIds s WHERE s = :siteId")
    List<Employe> findBySiteIdsContaining(@Param("siteId") String siteId);

    @Query("SELECT e FROM Employe e JOIN e.siteIds s WHERE s = :siteId")
    Page<Employe> findBySiteIdsContaining(@Param("siteId") String siteId, Pageable pageable);

    // Organisation-scoped queries
    Page<Employe> findByOrganisationId(String organisationId, Pageable pageable);
    Optional<Employe> findByIdAndOrganisationId(String id, String organisationId);

    @Query("SELECT e FROM Employe e JOIN e.siteIds s WHERE s = :siteId AND e.organisationId = :organisationId")
    List<Employe> findBySiteIdsContainingAndOrganisationId(@Param("siteId") String siteId, @Param("organisationId") String organisationId);

    @Query("SELECT e FROM Employe e JOIN e.siteIds s WHERE s = :siteId AND e.organisationId = :organisationId")
    Page<Employe> findBySiteIdsContainingAndOrganisationId(@Param("siteId") String siteId, @Param("organisationId") String organisationId, Pageable pageable);

    List<Employe> findByRoleAndOrganisationId(String role, String organisationId);
    Optional<Employe> findByPinHashAndOrganisationId(String pinHash, String organisationId);

    /** For kiosk PIN lookup — find by pinHash across all orgs, then filter by site */
    List<Employe> findByPinHash(String pinHash);
}

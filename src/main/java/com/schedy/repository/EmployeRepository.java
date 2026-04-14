package com.schedy.repository;

import com.schedy.entity.Employe;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
@Repository
public interface EmployeRepository extends JpaRepository<Employe, String> {
    /**
     * Sprint 16 / Feature 2 : role is now a @ElementCollection (List).
     * Returns employees who hold the given role at ANY position in their hierarchy.
     */
    @Query("SELECT DISTINCT e FROM Employe e JOIN e.roles r WHERE r = :role")
    List<Employe> findByRole(@Param("role") String role);

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
    /**
     * Sprint 16 / Feature 2 : tenant-scoped multi-role query. Returns employees
     * holding the given role at any position in their hierarchy, for the given org.
     */
    @Query("SELECT DISTINCT e FROM Employe e JOIN e.roles r WHERE r = :role AND e.organisationId = :organisationId")
    List<Employe> findByRoleAndOrganisationId(@Param("role") String role, @Param("organisationId") String organisationId);
    Optional<Employe> findByPinHashAndOrganisationId(String pinHash, String organisationId);

    /**
     * V36: returns ALL employees in the org with the given PIN hash. Used by
     * the per-employee PIN regeneration flow to detect collisions on the same
     * site. The Optional variant above silently returns the first match, which
     * is a latent bug when more than one employee shares a hash on the same
     * site (a kiosk lookup would then resolve to the wrong employee).
     */
    @Query("SELECT e FROM Employe e WHERE e.pinHash = :pinHash AND e.organisationId = :organisationId")
    List<Employe> findAllByPinHashAndOrganisationId(@Param("pinHash") String pinHash, @Param("organisationId") String organisationId);

    /** For kiosk PIN lookup — find by pinHash across all orgs, then filter by site */
    List<Employe> findByPinHash(String pinHash);
    long countByOrganisationId(String organisationId);
    @Query("SELECT e.organisationId, COUNT(e) FROM Employe e WHERE e.organisationId IN :orgIds GROUP BY e.organisationId")
    List<Object[]> countGroupedByOrganisationId(@Param("orgIds") Collection<String> orgIds);
    boolean existsByEmailAndOrganisationId(String email, String organisationId);
    Optional<Employe> findByEmailAndOrganisationId(String email, String organisationId);
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
}

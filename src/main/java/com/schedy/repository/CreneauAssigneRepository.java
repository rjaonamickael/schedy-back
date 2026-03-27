package com.schedy.repository;

import com.schedy.entity.CreneauAssigne;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreneauAssigneRepository extends JpaRepository<CreneauAssigne, String> {
    List<CreneauAssigne> findBySemaine(String semaine);
    List<CreneauAssigne> findByEmployeId(String employeId);
    List<CreneauAssigne> findByEmployeIdAndSemaine(String employeId, String semaine);
    void deleteBySemaine(String semaine);

    // Multi-site queries
    List<CreneauAssigne> findBySiteId(String siteId);
    List<CreneauAssigne> findBySemaineAndSiteId(String semaine, String siteId);
    List<CreneauAssigne> findByEmployeIdAndSiteId(String employeId, String siteId);
    List<CreneauAssigne> findByEmployeIdAndSemaineAndSiteId(String employeId, String semaine, String siteId);
    void deleteBySemaineAndSiteId(String semaine, String siteId);
    Page<CreneauAssigne> findBySiteId(String siteId, Pageable pageable);

    // Organisation-scoped queries
    Page<CreneauAssigne> findByOrganisationId(String organisationId, Pageable pageable);
    Optional<CreneauAssigne> findByIdAndOrganisationId(String id, String organisationId);
    List<CreneauAssigne> findBySemaineAndOrganisationId(String semaine, String organisationId);
    List<CreneauAssigne> findBySemaineAndSiteIdAndOrganisationId(String semaine, String siteId, String organisationId);
    List<CreneauAssigne> findByEmployeIdAndOrganisationId(String employeId, String organisationId);
    List<CreneauAssigne> findByEmployeIdAndSiteIdAndOrganisationId(String employeId, String siteId, String organisationId);
    List<CreneauAssigne> findByEmployeIdAndSemaineAndOrganisationId(String employeId, String semaine, String organisationId);
    List<CreneauAssigne> findByEmployeIdAndSemaineAndSiteIdAndOrganisationId(String employeId, String semaine, String siteId, String organisationId);
    void deleteBySemaineAndOrganisationId(String semaine, String organisationId);
    void deleteBySemaineAndSiteIdAndOrganisationId(String semaine, String siteId, String organisationId);
    Page<CreneauAssigne> findBySiteIdAndOrganisationId(String siteId, String organisationId, Pageable pageable);
    void deleteByEmployeIdAndOrganisationId(String employeId, String organisationId);
    void deleteByOrganisationId(String organisationId);

    /** Count shifts for a given employee in a specific week (current week). */
    long countByEmployeIdAndOrganisationIdAndSemaine(String employeId, String organisationId, String semaine);

    /**
     * Count shifts for a given employee in weeks strictly after the provided week string.
     * Week strings use ISO format "YYYY-Www" so lexicographic comparison works correctly.
     */
    @Query("SELECT COUNT(c) FROM CreneauAssigne c WHERE c.employeId = :employeId AND c.organisationId = :orgId AND c.semaine > :semaine")
    long countFutureByEmployeIdAndOrganisationId(@Param("employeId") String employeId, @Param("orgId") String orgId, @Param("semaine") String semaine);
}

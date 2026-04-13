package com.schedy.repository;

import com.schedy.entity.DemandeConge;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import com.schedy.entity.StatutDemande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
@Repository
public interface DemandeCongeRepository extends JpaRepository<DemandeConge, String> {
    List<DemandeConge> findByEmployeId(String employeId);
    Page<DemandeConge> findByEmployeId(String employeId, Pageable pageable);
    List<DemandeConge> findByStatut(StatutDemande statut);
    Page<DemandeConge> findByStatut(StatutDemande statut, Pageable pageable);
    // Organisation-scoped queries
    Page<DemandeConge> findByOrganisationId(String organisationId, Pageable pageable);
    List<DemandeConge> findByOrganisationId(String organisationId);
    Optional<DemandeConge> findByIdAndOrganisationId(String id, String organisationId);
    List<DemandeConge> findByEmployeIdAndOrganisationId(String employeId, String organisationId);
    @Modifying @Transactional
    void deleteByEmployeIdAndOrganisationId(String employeId, String organisationId);
    @Modifying @Transactional
    void deleteByOrganisationId(String organisationId);
    List<DemandeConge> findByTypeCongeIdAndOrganisationId(String typeCongeId, String organisationId);
    List<DemandeConge> findByTypeCongeIdAndStatutAndOrganisationId(String typeCongeId, StatutDemande statut, String organisationId);
    @Modifying @Transactional
    void deleteByTypeCongeIdAndOrganisationId(String typeCongeId, String organisationId);
    List<DemandeConge> findByOrganisationIdAndStatut(String organisationId, StatutDemande statut);
    /**
     * Returns approved leaves that overlap with the given date range [dateDebutMax, dateFinMin].
     * A leave overlaps the target week when its end date >= week start AND its start date <= week end.
     */
    List<DemandeConge> findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
            String organisationId, StatutDemande statut, LocalDate dateFinMin, LocalDate dateDebutMax);
    /** Count pending leave requests for a given employee. */
    long countByEmployeIdAndOrganisationIdAndStatut(String employeId, String organisationId, StatutDemande statut);
    /**
     * Count approved leave requests that have not yet ended (dateFin >= today).
     * These represent scheduled absences that would be orphaned after deletion.
     */
    @Query("SELECT COUNT(d) FROM DemandeConge d WHERE d.employeId = :employeId AND d.organisationId = :orgId AND d.statut = :statut AND d.dateFin >= :today")
    long countApprovedFutureByEmployeId(@Param("employeId") String employeId, @Param("orgId") String orgId, @Param("statut") StatutDemande statut, @Param("today") LocalDate today);
}

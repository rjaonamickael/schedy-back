package com.schedy.repository;

import com.schedy.entity.AbsenceImprevue;
import com.schedy.entity.StatutAbsenceImprevue;
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
public interface AbsenceImprevueRepository extends JpaRepository<AbsenceImprevue, String> {

    Optional<AbsenceImprevue> findByIdAndOrganisationId(String id, String organisationId);

    List<AbsenceImprevue> findByEmployeIdAndOrganisationId(String employeId, String organisationId);

    List<AbsenceImprevue> findByStatutAndOrganisationId(StatutAbsenceImprevue statut, String organisationId);

    List<AbsenceImprevue> findByStatutInAndOrganisationId(
            List<StatutAbsenceImprevue> statuts, String organisationId);

    List<AbsenceImprevue> findByOrganisationIdAndDateAbsence(String organisationId, LocalDate dateAbsence);

    Page<AbsenceImprevue> findByOrganisationId(String organisationId, Pageable pageable);

    @Query("""
        SELECT COUNT(a) FROM AbsenceImprevue a
        WHERE a.employeId = :employeId
          AND a.organisationId = :orgId
          AND a.statut IN ('SIGNALEE', 'VALIDEE', 'EN_COURS')
          AND a.dateAbsence >= :since
        """)
    long countActiveAbsencesForEmployee(
            @Param("employeId") String employeId,
            @Param("orgId") String orgId,
            @Param("since") LocalDate since);

    @Query("""
        SELECT COUNT(a) FROM AbsenceImprevue a
        WHERE a.employeId = :employeId
          AND a.organisationId = :orgId
          AND a.dateAbsence = :date
          AND a.statut NOT IN ('REFUSEE', 'ANNULEE')
        """)
    long countNonTerminalForEmployeeOnDate(
            @Param("employeId") String employeId,
            @Param("orgId") String orgId,
            @Param("date") LocalDate date);

    void deleteByOrganisationId(String organisationId);
}

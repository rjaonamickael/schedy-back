package com.schedy.repository;

import com.schedy.entity.Pointage;
import com.schedy.entity.StatutPointage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PointageRepository extends JpaRepository<Pointage, String> {
    List<Pointage> findByEmployeIdOrderByHorodatageDesc(String employeId);
    Page<Pointage> findByEmployeId(String employeId, Pageable pageable);
    List<Pointage> findByEmployeIdAndHorodatageBetweenOrderByHorodatageDesc(
            String employeId, OffsetDateTime start, OffsetDateTime end);
    List<Pointage> findByHorodatageBetweenOrderByHorodatageDesc(
            OffsetDateTime start, OffsetDateTime end);
    Optional<Pointage> findTopByEmployeIdOrderByHorodatageDesc(String employeId);
    List<Pointage> findByStatut(StatutPointage statut);

    // Multi-site queries
    List<Pointage> findBySiteId(String siteId);
    List<Pointage> findByEmployeIdAndSiteId(String employeId, String siteId);
    Page<Pointage> findBySiteId(String siteId, Pageable pageable);
    List<Pointage> findByStatutAndSiteId(StatutPointage statut, String siteId);
    List<Pointage> findBySiteIdAndHorodatageBetweenOrderByHorodatageDesc(
            String siteId, OffsetDateTime start, OffsetDateTime end);
    Optional<Pointage> findTopByEmployeIdAndSiteIdOrderByHorodatageDesc(String employeId, String siteId);

    // Organisation-scoped queries
    Page<Pointage> findByOrganisationId(String organisationId, Pageable pageable);
    List<Pointage> findByOrganisationId(String organisationId);
    Optional<Pointage> findByIdAndOrganisationId(String id, String organisationId);
    Page<Pointage> findBySiteIdAndOrganisationId(String siteId, String organisationId, Pageable pageable);
    List<Pointage> findByEmployeIdAndOrganisationId(String employeId, String organisationId);
    List<Pointage> findByEmployeIdAndSiteIdAndOrganisationId(String employeId, String siteId, String organisationId);
    List<Pointage> findByOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(String organisationId, OffsetDateTime start, OffsetDateTime end);
    List<Pointage> findBySiteIdAndOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(String siteId, String organisationId, OffsetDateTime start, OffsetDateTime end);
    List<Pointage> findByStatutAndOrganisationId(StatutPointage statut, String organisationId);
    List<Pointage> findByStatutAndSiteIdAndOrganisationId(StatutPointage statut, String siteId, String organisationId);
    Optional<Pointage> findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(String employeId, String organisationId);
    Optional<Pointage> findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(String employeId, String siteId, String organisationId);
    List<Pointage> findByEmployeIdAndOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(String employeId, String organisationId, OffsetDateTime start, OffsetDateTime end);
    void deleteByEmployeIdAndOrganisationId(String employeId, String organisationId);
}

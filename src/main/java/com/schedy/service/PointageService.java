package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.PointerRequest;
import com.schedy.dto.PointageDto;
import com.schedy.entity.Pointage;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.PointageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointageService {

    private final PointageRepository pointageRepository;
    private final TenantContext tenantContext;

    @Transactional(readOnly = true)
    public Page<Pointage> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public Pointage findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pointage", id));
    }

    @Transactional(readOnly = true)
    public List<Pointage> findByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByEmployeIdAndOrganisationId(employeId, orgId);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findTodayAll() {
        String orgId = tenantContext.requireOrganisationId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return pointageRepository.findByOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(orgId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findTodayAllBySite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return pointageRepository.findBySiteIdAndOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(siteId, orgId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    public Page<Pointage> findBySiteId(String siteId, Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findBySiteIdAndOrganisationId(siteId, orgId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findByEmployeIdAndSiteId(String employeId, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByEmployeIdAndSiteIdAndOrganisationId(employeId, siteId, orgId);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findTodayByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return pointageRepository.findByEmployeIdAndOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(
                employeId, orgId, startOfDay, endOfDay);
    }

    @Transactional
    public Pointage pointer(PointerRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        return buildAndSavePointage(request, orgId);
    }

    @Transactional
    public Pointage update(String id, PointageDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Pointage pointage = pointageRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pointage", id));
        if (dto.type() != null) pointage.setType(dto.type());
        if (dto.horodatage() != null) pointage.setHorodatage(dto.horodatage());
        if (dto.methode() != null) pointage.setMethode(dto.methode());
        if (dto.statut() != null) pointage.setStatut(dto.statut());
        pointage.setAnomalie(dto.anomalie());
        if ("corrige".equals(dto.statut()) || "valide".equals(dto.statut())) {
            pointage.setStatut(dto.statut());
        }
        return pointageRepository.save(pointage);
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Pointage pointage = pointageRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pointage", id));
        pointageRepository.delete(pointage);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findAnomalies() {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByStatutAndOrganisationId("anomalie", orgId);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findAnomaliesBySite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByStatutAndSiteIdAndOrganisationId("anomalie", siteId, orgId);
    }

    /**
     * Public kiosk flow: creates a pointage with an explicitly provided organisationId
     * (resolved from the PointageCode), since TenantContext is not available for public endpoints.
     */
    @Transactional
    public Pointage pointerFromKiosk(PointerRequest request, String organisationId) {
        return buildAndSavePointage(request, organisationId);
    }

    /**
     * Shared logic for building and saving a pointage, used by both pointer() and pointerFromKiosk().
     */
    private Pointage buildAndSavePointage(PointerRequest request, String orgId) {
        String employeId = request.employeId();
        String siteId = request.siteId();
        LocalDateTime now = LocalDateTime.now();

        // Determine entry or exit based on last pointage for this site
        Optional<Pointage> lastPointage;
        if (siteId != null) {
            lastPointage = pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(employeId, siteId, orgId);
        } else {
            lastPointage = pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(employeId, orgId);
        }
        String type;
        String anomalie = null;
        String statut = "valide";

        if (lastPointage.isEmpty() || "sortie".equals(lastPointage.get().getType())) {
            type = "entree";
        } else {
            type = "sortie";

            // Anomaly detection: check if last entry was more than 12 hours ago
            Pointage last = lastPointage.get();
            Duration duration = Duration.between(last.getHorodatage(), now);
            if (duration.toHours() >= 12) {
                anomalie = "Duree de travail anormalement longue (" + duration.toHours() + "h) - oubli de sortie possible";
                statut = "anomalie";
                log.warn("Anomaly detected for employe {}: {}", employeId, anomalie);
            }
        }

        // Anomaly: double pointage (same type within 1 minute)
        if (lastPointage.isPresent()) {
            Pointage last = lastPointage.get();
            Duration sinceLast = Duration.between(last.getHorodatage(), now);
            if (sinceLast.toMinutes() < 1) {
                anomalie = "Double pointage detecte (moins d'une minute entre deux pointages)";
                statut = "anomalie";
                log.warn("Double pointage anomaly detected for employe {}", employeId);
            }
        }

        Pointage pointage = Pointage.builder()
                .employeId(employeId)
                .type(type)
                .horodatage(now)
                .methode(request.methode())
                .siteId(siteId)
                .organisationId(orgId)
                .statut(statut)
                .anomalie(anomalie)
                .build();

        Pointage saved = pointageRepository.save(pointage);
        log.info("Pointage created: employe={}, type={}, site={}, statut={}", employeId, type, siteId, statut);
        return saved;
    }
}

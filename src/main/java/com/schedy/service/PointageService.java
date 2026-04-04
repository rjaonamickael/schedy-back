package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.PointerRequest;
import com.schedy.dto.request.PointageManuelRequest;
import com.schedy.dto.PointageDto;
import com.schedy.entity.*;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.PointageRepository;
import com.schedy.repository.UserRepository;
import com.schedy.util.LocaleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointageService {

    private final PointageRepository pointageRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final OrganisationRepository organisationRepository;

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
        ZoneId zone = resolveOrgZone(orgId);
        OffsetDateTime startOfDay = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endOfDay = LocalDate.now(zone).atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();
        return pointageRepository.findByOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(orgId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findTodayAllBySite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        ZoneId zone = resolveOrgZone(orgId);
        OffsetDateTime startOfDay = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endOfDay = LocalDate.now(zone).atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();
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
        ZoneId zone = resolveOrgZone(orgId);
        OffsetDateTime startOfDay = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endOfDay = LocalDate.now(zone).atTime(LocalTime.MAX).atZone(zone).toOffsetDateTime();
        return pointageRepository.findByEmployeIdAndOrganisationIdAndHorodatageBetweenOrderByHorodatageDesc(
                employeId, orgId, startOfDay, endOfDay);
    }

    @Transactional
    public Pointage pointer(PointerRequest request) {
        String orgId = tenantContext.requireOrganisationId();

        // B-04: Ownership check — EMPLOYEE may only clock in/out for themselves.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"))) {
            String callerEmail = auth.getName();
            String callerEmployeId = userRepository.findByEmail(callerEmail)
                    .map(u -> u.getEmployeId())
                    .orElse(null);
            if (callerEmployeId == null || !callerEmployeId.equals(request.employeId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Un employe ne peut pointer que pour lui-meme");
            }
        }

        return buildAndSavePointage(request, orgId);
    }

    @Transactional
    public Pointage pointerManuel(PointageManuelRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        Pointage pointage = Pointage.builder()
                .employeId(request.employeId())
                .type(parseTypePointage(request.type()))
                .horodatage(request.horodatage())
                .methode(parseMethodePointage(request.methode()))
                .siteId(request.siteId())
                .organisationId(orgId)
                .statut(StatutPointage.valide)
                .build();
        Pointage saved = pointageRepository.save(pointage);
        log.info("Pointage manuel created: employe={}, type={}, site={}, horodatage={}",
                request.employeId(), request.type(), request.siteId(), request.horodatage());
        return saved;
    }

    @Transactional
    public Pointage update(String id, PointageDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Pointage pointage = pointageRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pointage", id));
        if (dto.type() != null) pointage.setType(parseTypePointage(dto.type()));
        if (dto.horodatage() != null) pointage.setHorodatage(dto.horodatage());
        if (dto.methode() != null) pointage.setMethode(parseMethodePointage(dto.methode()));
        if (dto.statut() != null) {
            pointage.setStatut(parseStatutPointage(dto.statut()));
            // When marking as corrected and no explicit anomalie value was provided,
            // clear the anomalie — the correction resolves it.
            if ("corrige".equals(dto.statut()) && dto.anomalie() == null) {
                pointage.setAnomalie(null);
            }
        }
        if (dto.anomalie() != null) pointage.setAnomalie(dto.anomalie());
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
        return pointageRepository.findByStatutAndOrganisationId(StatutPointage.anomalie, orgId);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findAnomaliesBySite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByStatutAndSiteIdAndOrganisationId(StatutPointage.anomalie, siteId, orgId);
    }

    /**
     * Public kiosk flow: creates a pointage with an explicitly provided organisationId
     * (resolved from the PointageCode), since TenantContext is not available for public endpoints.
     */
    @Transactional
    public Pointage pointerFromKiosk(PointerRequest request, String organisationId) {
        return buildAndSavePointage(request, organisationId);
    }

    private TypePointage parseTypePointage(String value) {
        try {
            return TypePointage.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new com.schedy.exception.BusinessRuleException("Valeur invalide pour type pointage: " + value);
        }
    }

    private MethodePointage parseMethodePointage(String value) {
        try {
            return MethodePointage.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new com.schedy.exception.BusinessRuleException("Valeur invalide pour methode pointage: " + value);
        }
    }

    private StatutPointage parseStatutPointage(String value) {
        try {
            return StatutPointage.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new com.schedy.exception.BusinessRuleException("Valeur invalide pour statut pointage: " + value);
        }
    }

    /**
     * Resolves the organisation's timezone from its {@code pays} field (B-22).
     * Falls back to UTC when the organisation is not found or has no pays set.
     * This is a single-row primary-key lookup and is covered by the JPA first-level cache
     * within the same transaction, so it adds no measurable overhead.
     */
    private ZoneId resolveOrgZone(String orgId) {
        return organisationRepository.findById(orgId)
                .map(org -> LocaleUtils.zoneIdFromPays(org.getPays()))
                .orElse(ZoneOffset.UTC);
    }

    /**
     * Shared logic for building and saving a pointage, used by both pointer() and pointerFromKiosk().
     */
    private Pointage buildAndSavePointage(PointerRequest request, String orgId) {
        String employeId = request.employeId();
        String siteId = request.siteId();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Determine entry or exit based on last pointage for this site
        Optional<Pointage> lastPointage;
        if (siteId != null) {
            lastPointage = pointageRepository.findTopByEmployeIdAndSiteIdAndOrganisationIdOrderByHorodatageDesc(employeId, siteId, orgId);
        } else {
            lastPointage = pointageRepository.findTopByEmployeIdAndOrganisationIdOrderByHorodatageDesc(employeId, orgId);
        }
        TypePointage type;
        String anomalie = null;
        StatutPointage statut = StatutPointage.valide;

        if (lastPointage.isEmpty() || lastPointage.get().getType() == TypePointage.sortie) {
            type = TypePointage.entree;
        } else {
            type = TypePointage.sortie;

            // Anomaly detection: check if last entry was more than 12 hours ago
            Pointage last = lastPointage.get();
            Duration duration = Duration.between(last.getHorodatage(), now);
            if (duration.toHours() >= 12) {
                anomalie = "Duree de travail anormalement longue (" + duration.toHours() + "h) - oubli de sortie possible";
                statut = StatutPointage.anomalie;
                log.warn("Anomaly detected for employe {}: {}", employeId, anomalie);
            }
        }

        // Anomaly: double pointage (same type within 1 minute)
        if (lastPointage.isPresent()) {
            Pointage last = lastPointage.get();
            Duration sinceLast = Duration.between(last.getHorodatage(), now);
            if (sinceLast.toMinutes() < 1) {
                anomalie = "Double pointage detecte (moins d'une minute entre deux pointages)";
                statut = StatutPointage.anomalie;
                log.warn("Double pointage anomaly detected for employe {}", employeId);
            }
        }

        Pointage pointage = Pointage.builder()
                .employeId(employeId)
                .type(type)
                .horodatage(now)
                .methode(parseMethodePointage(request.methode()))
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

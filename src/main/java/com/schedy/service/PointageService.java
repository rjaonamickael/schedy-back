package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.PointerRequest;
import com.schedy.dto.request.PointageManuelRequest;
import com.schedy.dto.PointageDto;
import com.schedy.entity.*;
import com.schedy.exception.ClockInNotAuthorizedException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.CreneauAssigneRepository;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.ParametresRepository;
import com.schedy.repository.PointageRepository;
import com.schedy.repository.UserRepository;
import com.schedy.util.LocaleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
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
    private final PauseService pauseService;
    private final CreneauAssigneRepository creneauRepository;
    private final ParametresRepository parametresRepository;

    @Transactional(readOnly = true)
    public Page<Pointage> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return pointageRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public Pointage findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Pointage pointage = pointageRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pointage", id));
        // SEC-11: EMPLOYEE may only read their own pointage (prevents IDOR enumeration)
        checkOwnershipIfEmployee(pointage.getEmployeId());
        return pointage;
    }

    @Transactional(readOnly = true)
    public List<Pointage> findByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        // SEC-11: EMPLOYEE may only read their own pointages
        checkOwnershipIfEmployee(employeId);
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
        // SEC-11: EMPLOYEE may only read their own pointages
        checkOwnershipIfEmployee(employeId);
        return pointageRepository.findByEmployeIdAndSiteIdAndOrganisationId(employeId, siteId, orgId);
    }

    @Transactional(readOnly = true)
    public List<Pointage> findTodayByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        // SEC-11: EMPLOYEE may only read their own pointages
        checkOwnershipIfEmployee(employeId);
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
     *
     * <p><b>Security — creneau guard.</b> Before any pointage is persisted the
     * employee must have an active creneau at the target site:
     * <ol>
     *   <li>the creneau belongs to this employee + this site + current ISO
     *       week + current day-of-week,</li>
     *   <li>current time (in the organisation's timezone) falls within
     *       {@code [heureDebut - toleranceAvant, heureFin + toleranceApres]}.</li>
     * </ol>
     * If either check fails the clock-in is rejected with a generic
     * {@link ClockInNotAuthorizedException} — the concrete reason is preserved
     * only in server-side audit logs so a kiosk operator cannot distinguish
     * between "wrong PIN", "wrong site" or "not scheduled now" (info-leak
     * guard — see audit trail in the WARN log line).
     */
    @Transactional
    public Pointage pointerFromKiosk(PointerRequest request, String organisationId) {
        enforceCreneauGuard(request, organisationId);
        return buildAndSavePointage(request, organisationId);
    }

    /**
     * Creneau guard for kiosk clock-ins. Runs a single atomic repository
     * query against the current transactional snapshot (no TOCTOU) and
     * throws {@link ClockInNotAuthorizedException} with a precise audit
     * reason when the employee is not allowed to clock in.
     */
    private void enforceCreneauGuard(PointerRequest request, String organisationId) {
        final String employeId = request.employeId();
        final String siteId = request.siteId();
        if (employeId == null || siteId == null) {
            rejectAndLog(employeId, siteId, "null employeId or siteId",
                    ClockInNotAuthorizedException.Reason.UNKNOWN);
        }

        // Resolve "now" in the organisation's timezone — creneaux are stored
        // as decimal hours relative to the org's local calendar, so UTC or a
        // hard-coded zone would mis-align at day boundaries.
        final ZoneId orgZone = resolveOrgZone(organisationId);
        final LocalDateTime nowLocal = LocalDateTime.now(orgZone);
        final LocalDate today = nowLocal.toLocalDate();

        final String semaine = toIsoWeek(today);
        final int jour = today.getDayOfWeek().getValue() - 1; // 0=Mon..6=Sun
        final double heureNow = nowLocal.toLocalTime().getHour()
                + nowLocal.toLocalTime().getMinute() / 60.0;

        // Load per-org tolerance (site-scoped params override org defaults if present).
        final Parametres params = parametresRepository
                .findBySiteIdAndOrganisationId(siteId, organisationId)
                .or(() -> parametresRepository.findBySiteIdIsNullAndOrganisationId(organisationId))
                .orElse(null);
        final int toleranceBeforeMinutes = params != null && params.getToleranceAvantShiftMinutes() != null
                ? params.getToleranceAvantShiftMinutes() : 30;
        final int toleranceAfterMinutes = params != null && params.getToleranceApresShiftMinutes() != null
                ? params.getToleranceApresShiftMinutes() : 30;
        final double toleranceBeforeH = toleranceBeforeMinutes / 60.0;
        final double toleranceAfterH = toleranceAfterMinutes / 60.0;

        final List<CreneauAssigne> active = creneauRepository.findActiveForClockIn(
                organisationId, employeId, siteId, semaine, jour,
                heureNow, toleranceBeforeH, toleranceAfterH);

        if (!active.isEmpty()) {
            log.info("Clock-in authorized: employe={} site={} jour={} heure={} creneaux={}",
                    employeId, siteId, jour, heureNow, active.size());
            return;
        }

        // Empty result: distinguish NO_CRENEAU_TODAY vs OUTSIDE_TOLERANCE_WINDOW
        // for the audit log (client always sees the generic message).
        final List<CreneauAssigne> sameDay = creneauRepository
                .findByEmployeIdAndSemaineAndSiteIdAndOrganisationId(employeId, semaine, siteId, organisationId)
                .stream()
                .filter(c -> c.getJour() == jour)
                .toList();
        final ClockInNotAuthorizedException.Reason reason = sameDay.isEmpty()
                ? ClockInNotAuthorizedException.Reason.NO_CRENEAU_TODAY
                : ClockInNotAuthorizedException.Reason.OUTSIDE_TOLERANCE_WINDOW;

        rejectAndLog(employeId, siteId,
                String.format("jour=%d heure=%.2f tolBefore=%dmin tolAfter=%dmin sameDayCreneaux=%d",
                        jour, heureNow, toleranceBeforeMinutes, toleranceAfterMinutes, sameDay.size()),
                reason);
    }

    /** Builds the audit log entry and throws the generic exception. */
    private void rejectAndLog(String employeId, String siteId, String detail,
                              ClockInNotAuthorizedException.Reason reason) {
        log.warn("Clock-in REJECTED reason={} employe={} site={} detail={{{}}}",
                reason, employeId, siteId, detail);
        throw new ClockInNotAuthorizedException(reason);
    }

    /** ISO-8601 week label matching the frontend/planning service convention. */
    private static String toIsoWeek(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
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

        // Layer 3 pause auto-detection: when an employee clocks back in after a sortie,
        // check the gap and record a DETECTE pause if it fits the org's detection window.
        // Runs within the same transaction — failures are caught by the service itself.
        if (saved.getType() == TypePointage.entree && saved.getStatut() == StatutPointage.valide) {
            pauseService.detectFromPointage(saved);
        }

        return saved;
    }

    /**
     * SEC-11: Enforces row-level ownership for the EMPLOYEE role on pointage reads.
     *
     * <p>ADMIN and MANAGER may read any pointage in their organisation (tenant scoping
     * already enforced upstream). EMPLOYEE may only read pointages that belong to
     * themselves; any attempt to enumerate IDs and access another employee's data
     * results in a 403 Forbidden response.
     *
     * <p>The check is intentionally lenient when no SecurityContext is present
     * (e.g. unit tests that exercise the service without setting up authentication)
     * — in that case the call is treated as a trusted internal invocation.
     *
     * @param targetEmployeId the employeId of the resource being accessed
     * @throws ResponseStatusException 403 Forbidden if the caller is an EMPLOYEE
     *         and {@code targetEmployeId} does not match their own employeId
     */
    private void checkOwnershipIfEmployee(String targetEmployeId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return;
        }
        boolean isEmployee = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_EMPLOYEE"));
        if (!isEmployee) {
            return;
        }
        // Caller is strictly an EMPLOYEE — verify ownership
        String callerEmail = auth.getName();
        String callerEmployeId = userRepository.findByEmail(callerEmail)
                .map(User::getEmployeId)
                .orElse(null);
        if (callerEmployeId == null || !callerEmployeId.equals(targetEmployeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acces refuse: vous ne pouvez consulter que vos propres donnees");
        }
    }
}

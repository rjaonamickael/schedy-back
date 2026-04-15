package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.PauseCreateRequest;
import com.schedy.entity.Parametres;
import com.schedy.entity.Pause;
import com.schedy.entity.Pointage;
import com.schedy.entity.SourcePause;
import com.schedy.entity.StatutPause;
import com.schedy.entity.TypePause;
import com.schedy.entity.TypePointage;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.ParametresRepository;
import com.schedy.repository.PauseRepository;
import com.schedy.repository.PointageRepository;
import com.schedy.util.LocaleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Orchestrates the Pause lifecycle :
 *
 * <ul>
 *   <li><b>Layer 3 auto-detection</b> (see {@link #detectFromPointage}) :
 *       whenever a fresh {@code entree} pointage is created, inspect the gap
 *       against the previous {@code sortie} and, if it falls within the
 *       configured detection window, create a {@code DETECTE} pause for
 *       manager confirmation.</li>
 *   <li><b>Manual entry</b> (see {@link #creer}) : lets a manager explicitly
 *       record a pause that fell outside the auto-detection window, or cover
 *       historical corrections.</li>
 *   <li><b>Confirm / contest workflow</b> (see {@link #confirmer},
 *       {@link #contester}) : manager actions on {@code DETECTE} rows.</li>
 * </ul>
 *
 * <p>Detection is called synchronously from {@link PointageService} within
 * the same transaction as the pointage creation — a detection failure is
 * logged but never bubbles up, so a broken detection rule can't block a
 * clock-in/out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PauseService {

    /** Default detection window when no Parametres row is found for the site/org. */
    private static final int DEFAULT_FENETRE_MIN_MINUTES = 15;
    private static final int DEFAULT_FENETRE_MAX_MINUTES = 90;

    /** Gap at or above this threshold defaults the detected pause to REPAS (meal). */
    static final int REPAS_THRESHOLD_MINUTES = 30;

    private final PauseRepository pauseRepository;
    private final PointageRepository pointageRepository;
    private final ParametresRepository parametresRepository;
    private final TenantContext tenantContext;
    private final OrganisationRepository organisationRepository;

    // ─────────────────────────────────────────────────────────────────────
    // READS
    // ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Pause> findByDate(String date) {
        return findByDate(date, null);
    }

    /**
     * Returns pauses for the caller's organisation on the given date.
     * When {@code siteId} is non-null the result is narrowed to that site only,
     * enabling multi-site dashboards without breaking single-site callers.
     *
     * <p>MED-02: Day boundaries are now computed in the organisation's local timezone
     * (via {@link LocaleUtils#zoneIdFromPays}) rather than UTC, matching the behaviour
     * of {@link PointageService}. For orgs in UTC+3 (Madagascar) a "day" query at UTC
     * midnight would otherwise miss the first 3 hours of the local business day.
     */
    @Transactional(readOnly = true)
    public List<Pause> findByDate(String date, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        ZoneId orgZone = resolveOrgZone(orgId);
        LocalDate ld = LocalDate.parse(date);
        OffsetDateTime startOfDay = ld.atStartOfDay(orgZone).toOffsetDateTime();
        OffsetDateTime endOfDay = ld.atTime(LocalTime.MAX).atZone(orgZone).toOffsetDateTime();

        if (siteId != null && !siteId.isBlank()) {
            return pauseRepository.findBySiteIdAndOrganisationIdAndDebutBetween(
                    siteId, orgId, startOfDay, endOfDay);
        }
        return pauseRepository.findByOrganisationIdAndDebutBetween(orgId, startOfDay, endOfDay);
    }

    // ─────────────────────────────────────────────────────────────────────
    // WRITES
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Manager-initiated manual pause creation. The resulting pause is marked
     * {@code source=MANUEL, statut=CONFIRME} and counts immediately for
     * payroll aggregates — the manager is explicitly stating the fact.
     *
     * <p>Uses the caller's {@link TenantContext} orgId ; callers from admin
     * endpoints are expected to hold {@code ROLE_ADMIN} or {@code ROLE_MANAGER}.
     */
    @Transactional
    public Pause creer(PauseCreateRequest request) {
        String orgId = tenantContext.requireOrganisationId();

        if (!request.fin().isAfter(request.debut())) {
            throw new BusinessRuleException("La fin de pause doit etre strictement apres le debut");
        }
        int dureeMinutes = (int) Math.max(0, Duration.between(request.debut(), request.fin()).toMinutes());
        if (dureeMinutes <= 0) {
            throw new BusinessRuleException("Duree de pause invalide");
        }

        String caller = SecurityContextHolder.getContext().getAuthentication().getName();

        Pause pause = Pause.builder()
                .employeId(request.employeId())
                .siteId(request.siteId())
                .organisationId(orgId)
                .debut(request.debut())
                .fin(request.fin())
                .dureeMinutes(dureeMinutes)
                .type(request.type())
                .source(SourcePause.MANUEL)
                .statut(StatutPause.CONFIRME)
                .payee(request.payee())
                .confirmeParId(caller)
                .confirmeAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        Pause saved = pauseRepository.save(pause);
        log.info("Pause manuelle creee: id={}, employe={}, duree={}min, type={}, payee={}, manager={}",
                saved.getId(), saved.getEmployeId(), saved.getDureeMinutes(),
                saved.getType(), saved.isPayee(), caller);
        return saved;
    }

    /**
     * Hard-delete a pause. Used by managers to remove an erroneous detection
     * (e.g., a Layer 3 false positive caused by a forgotten entree/sortie pair
     * that was actually a shift change, not a break).
     */
    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Pause pause = pauseRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pause", id));
        pauseRepository.delete(pause);
        log.info("Pause deleted: id={}, employe={}", id, pause.getEmployeId());
    }

    @Transactional
    public Pause confirmer(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Pause pause = pauseRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pause", id));

        if (pause.getStatut() != StatutPause.DETECTE) {
            throw new BusinessRuleException("Seules les pauses détectées peuvent être confirmées");
        }

        String caller = SecurityContextHolder.getContext().getAuthentication().getName();
        pause.setStatut(StatutPause.CONFIRME);
        pause.setConfirmeParId(caller);
        pause.setConfirmeAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pauseRepository.save(pause);
    }

    @Transactional
    public Pause contester(String id, String motif) {
        String orgId = tenantContext.requireOrganisationId();
        Pause pause = pauseRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pause", id));

        if (pause.getStatut() != StatutPause.DETECTE) {
            throw new BusinessRuleException("Seules les pauses détectées peuvent être contestées");
        }

        String caller = SecurityContextHolder.getContext().getAuthentication().getName();
        pause.setStatut(StatutPause.CONTESTE);
        pause.setMotifContestation(motif);
        pause.setConfirmeParId(caller);
        pause.setConfirmeAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pauseRepository.save(pause);
    }

    // ─────────────────────────────────────────────────────────────────────
    // LAYER 3 — AUTO-DETECTION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Invoked by {@link PointageService} right after a new {@code entree}
     * pointage has been persisted. Looks for the immediately preceding
     * {@code sortie} on the same employee (+ site when applicable) and,
     * if the gap falls inside the organisation's detection window, records
     * a new {@code DETECTE} pause ready for manager confirmation.
     *
     * <p>Design choices:
     * <ul>
     *   <li><b>Synchronous</b> — runs in the same transaction as the
     *       pointage, so either both rows commit or neither does. Keeps the
     *       audit trail consistent.</li>
     *   <li><b>Resilient</b> — all failures are caught and logged without
     *       bubbling up. A broken detection rule must never block a
     *       clock-in/out event.</li>
     *   <li><b>Duplicate-safe</b> — uses
     *       {@link PauseRepository#findByPointageEntreeIdAndPointageSortieId}
     *       to avoid double-inserts if the hook is fired twice for the same
     *       pointage pair.</li>
     *   <li><b>Type guess</b> — gap ≥ 30 min → {@code REPAS}, otherwise
     *       {@code PAUSE}. Matches common labor convention. The manager may
     *       contest and change this via the UI.</li>
     *   <li><b>Payee default</b> — {@code false}. Most meal breaks are
     *       unpaid ; manager override is the expected correction path.</li>
     * </ul>
     *
     * @param entree freshly persisted pointage of type {@link TypePointage#entree}
     */
    public void detectFromPointage(Pointage entree) {
        try {
            detectFromPointageInternal(entree);
        } catch (RuntimeException e) {
            // Never let a detection failure break the pointage commit.
            log.warn("Layer 3 pause detection failed for pointage {}: {}",
                    entree.getId(), e.getMessage(), e);
        }
    }

    private void detectFromPointageInternal(Pointage entree) {
        if (entree == null || entree.getType() != TypePointage.entree) {
            return;
        }
        String orgId = entree.getOrganisationId();
        String employeId = entree.getEmployeId();
        String siteId = entree.getSiteId();
        if (orgId == null || employeId == null) {
            return;
        }

        Optional<Pointage> maybePrev;
        if (siteId != null) {
            maybePrev = pointageRepository
                    .findTopByEmployeIdAndSiteIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            employeId, siteId, orgId, entree.getHorodatage());
        } else {
            maybePrev = pointageRepository
                    .findTopByEmployeIdAndOrganisationIdAndHorodatageLessThanOrderByHorodatageDesc(
                            employeId, orgId, entree.getHorodatage());
        }
        if (maybePrev.isEmpty()) {
            return;
        }
        Pointage prev = maybePrev.get();
        if (prev.getType() != TypePointage.sortie) {
            // Previous event wasn't a sortie — gap isn't interpretable as a pause.
            return;
        }
        // Duplicate guard : if a pause already links this exact pair, skip.
        if (pauseRepository
                .findByPointageEntreeIdAndPointageSortieId(entree.getId(), prev.getId())
                .isPresent()) {
            return;
        }

        long gapMinutes = Duration.between(prev.getHorodatage(), entree.getHorodatage()).toMinutes();
        if (gapMinutes <= 0) return;

        int[] window = resolveDetectionWindow(orgId, siteId);
        int min = window[0];
        int max = window[1];
        if (gapMinutes < min || gapMinutes > max) {
            // Gap outside the detection window — either a legit micro-tap or a full shift change.
            return;
        }

        TypePause guessedType = gapMinutes >= REPAS_THRESHOLD_MINUTES
                ? TypePause.REPAS
                : TypePause.PAUSE;

        Pause pause = Pause.builder()
                .employeId(employeId)
                .siteId(siteId)
                .organisationId(orgId)
                .debut(prev.getHorodatage())
                .fin(entree.getHorodatage())
                .dureeMinutes((int) gapMinutes)
                .type(guessedType)
                .source(SourcePause.DETECTION_AUTO)
                .statut(StatutPause.DETECTE)
                .payee(false)
                .pointageSortieId(prev.getId())
                .pointageEntreeId(entree.getId())
                .build();

        Pause saved = pauseRepository.save(pause);
        log.info("Pause auto-detectee: id={}, employe={}, site={}, gap={}min, type={}, source=DETECTION_AUTO",
                saved.getId(), employeId, siteId, gapMinutes, guessedType);
    }

    /**
     * Resolves the detection window [min, max] in minutes for the given
     * (org, site) pair. Site-scoped Parametres win over org-wide ; falls
     * back to {@code [15, 90]} when no row is found.
     */
    int[] resolveDetectionWindow(String orgId, String siteId) {
        Optional<Parametres> params = Optional.empty();
        if (siteId != null) {
            params = parametresRepository.findBySiteIdAndOrganisationId(siteId, orgId);
        }
        if (params.isEmpty()) {
            params = parametresRepository.findBySiteIdIsNullAndOrganisationId(orgId);
        }
        int min = params.map(Parametres::getFenetrePauseMinMinutes).orElse(DEFAULT_FENETRE_MIN_MINUTES);
        int max = params.map(Parametres::getFenetrePauseMaxMinutes).orElse(DEFAULT_FENETRE_MAX_MINUTES);
        if (min < 0) min = DEFAULT_FENETRE_MIN_MINUTES;
        if (max < min) max = Math.max(min, DEFAULT_FENETRE_MAX_MINUTES);
        return new int[] { min, max };
    }

    /**
     * Resolves the organisation's timezone from its {@code pays} field (MED-02).
     * Falls back to UTC when the organisation is not found or has no pays set.
     */
    private ZoneId resolveOrgZone(String orgId) {
        return organisationRepository.findById(orgId)
                .map(org -> LocaleUtils.zoneIdFromPays(org.getPays()))
                .orElse(ZoneOffset.UTC);
    }
}

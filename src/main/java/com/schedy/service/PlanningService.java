package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.CreneauAssigneDto;
import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.StatutDemande;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.CreneauAssigneRepository;
import com.schedy.repository.DemandeCongeRepository;
import com.schedy.repository.EmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningService {

    private final CreneauAssigneRepository creneauRepository;
    private final EmployeRepository employeRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final TenantContext tenantContext;

    /** Maximum hours allowed in a single creneau (sanity bound; org parametres can be stricter). */
    private static final double MAX_HEURES_CRENEAU = 16.0;

    /**
     * Paginated, admin-oriented view (no employee filter). Les endpoints métier
     * par semaine/employé passent par filterPublieForCaller ; findAll n'est pas
     * utilisé par le dashboard employé.
     */
    @Transactional(readOnly = true)
    public Page<CreneauAssigne> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public CreneauAssigne findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        CreneauAssigne c = creneauRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", id));
        if (isCallerEmployee() && !c.isPublie()) {
            throw new ResourceNotFoundException("Creneau", id);
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findBySemaine(String semaine) {
        String orgId = tenantContext.requireOrganisationId();
        return filterPublieForCaller(creneauRepository.findBySemaineAndOrganisationId(semaine, orgId));
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findBySemaineAndSite(String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return filterPublieForCaller(creneauRepository.findBySemaineAndSiteIdAndOrganisationId(semaine, siteId, orgId));
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        return filterPublieForCaller(creneauRepository.findByEmployeIdAndOrganisationId(employeId, orgId));
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeIdAndSite(String employeId, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return filterPublieForCaller(creneauRepository.findByEmployeIdAndSiteIdAndOrganisationId(employeId, siteId, orgId));
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeIdAndSemaine(String employeId, String semaine) {
        String orgId = tenantContext.requireOrganisationId();
        return filterPublieForCaller(creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(employeId, semaine, orgId));
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeIdAndSemaineAndSite(String employeId, String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return filterPublieForCaller(creneauRepository.findByEmployeIdAndSemaineAndSiteIdAndOrganisationId(employeId, semaine, siteId, orgId));
    }

    @Transactional(readOnly = true)
    public Page<CreneauAssigne> findBySite(String siteId, Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findBySiteIdAndOrganisationId(siteId, orgId, pageable);
    }

    @Transactional
    public CreneauAssigne create(CreneauAssigneDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        // Idempotent vis-à-vis du doublon exact : si un créneau strictement
        // identique existe déjà (cf. contrainte unique V28), on le renvoie tel
        // quel au lieu de laisser éclater une DataIntegrityViolationException.
        return creneauRepository.findExactMatch(
                        orgId, dto.employeId(), dto.semaine(), dto.jour(),
                        dto.siteId(), dto.heureDebut(), dto.heureFin())
                .orElseGet(() -> creneauRepository.save(
                        CreneauAssigne.builder()
                                .employeId(dto.employeId())
                                .jour(dto.jour())
                                .heureDebut(dto.heureDebut())
                                .heureFin(dto.heureFin())
                                .semaine(dto.semaine())
                                .siteId(dto.siteId())
                                .role(dto.role())
                                .organisationId(orgId)
                                // V47 : nouveau créneau = brouillon par défaut
                                .publie(false)
                                .build()));
    }

    @Transactional
    public List<CreneauAssigne> createBatch(List<CreneauAssigneDto> dtos) {
        String orgId = tenantContext.requireOrganisationId();
        List<CreneauAssigne> creneaux = dtos.stream().map(dto ->
                CreneauAssigne.builder()
                        .employeId(dto.employeId())
                        .jour(dto.jour())
                        .heureDebut(dto.heureDebut())
                        .heureFin(dto.heureFin())
                        .semaine(dto.semaine())
                        .siteId(dto.siteId())
                        .role(dto.role())
                        .organisationId(orgId)
                        // V47 : nouveau créneau = brouillon par défaut
                        .publie(false)
                        .build()
        ).toList();
        return creneauRepository.saveAll(creneaux);
    }

    /**
     * Updates a creneau (used by drag-and-drop and admin edits).
     *
     * <p><b>BE-04 / V33-01-02 SEC</b> : this method enforces business rules that v32 was
     * shipping bare. Each rule throws {@link BusinessRuleException} (HTTP 422 Unprocessable
     * Entity per GlobalExceptionHandler) with a clear French message so the frontend can
     * show actionable feedback.</p>
     *
     * <p>Rules enforced :</p>
     * <ol>
     *   <li><b>Org isolation</b> : both the existing creneau AND the target employe must belong
     *       to the current org (cross-org IDOR prevention).</li>
     *   <li><b>Time sanity</b> : heureFin > heureDebut, both in [0, 24], duration <= 16h.</li>
     *   <li><b>Overlap</b> : the new (employe, semaine, jour, heureDebut..heureFin) must not
     *       overlap any OTHER creneau already assigned to the same employe.</li>
     *   <li><b>Approved leave conflict</b> : the target date (semaine + jour) must not fall
     *       within an already-approved DemandeConge for the target employe.</li>
     * </ol>
     *
     * <p><b>V33-05 SEC</b> : on success, the change is logged in a structured slf4j format
     * (key=value pairs) so a future log parser can reconstruct who moved what.</p>
     *
     * <p><b>V47</b> : toute édition d'un créneau sur semaine courante ou future repasse
     * le créneau en brouillon (publie=false). Les éditions d'historique (semaine passée)
     * laissent publie inchangé pour ne pas faire disparaître le planning historique des
     * employés.</p>
     */
    @Transactional
    public CreneauAssigne update(String id, CreneauAssigneDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        CreneauAssigne creneau = creneauRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", id));

        // Snapshot for audit log (before mutation)
        String prevEmployeId = creneau.getEmployeId();
        int prevJour = creneau.getJour();
        double prevDebut = creneau.getHeureDebut();
        double prevFin = creneau.getHeureFin();
        String prevSemaine = creneau.getSemaine();
        String prevSiteId = creneau.getSiteId();
        boolean prevPublie = creneau.isPublie();

        // Run all validations against the proposed payload BEFORE touching the entity
        validateCreneauUpdate(orgId, id, dto);

        creneau.setEmployeId(dto.employeId());
        creneau.setJour(dto.jour());
        creneau.setHeureDebut(dto.heureDebut());
        creneau.setHeureFin(dto.heureFin());
        creneau.setSemaine(dto.semaine());
        creneau.setSiteId(dto.siteId());
        // Sprint 16 / Feature 2 : capture which role the employee is filling on this creneau.
        creneau.setRole(dto.role());

        // V47 : repasser en brouillon uniquement si l'édition concerne une
        // semaine courante ou future. Les éditions sur historique (semaine
        // passée) laissent publie inchangé.
        if (isSemaineCurrentOrFuture(dto.semaine())) {
            creneau.setPublie(false);
        }
        CreneauAssigne saved = creneauRepository.save(creneau);

        // V33-05 SEC : structured audit log (no PII beyond IDs)
        log.info(
            "audit.creneau.update orgId={} creneauId={} prev=[employe={},semaine={},jour={},h={}-{},site={},publie={}] new=[employe={},semaine={},jour={},h={}-{},site={},publie={}]",
            orgId, id,
            prevEmployeId, prevSemaine, prevJour, prevDebut, prevFin, prevSiteId, prevPublie,
            dto.employeId(), dto.semaine(), dto.jour(), dto.heureDebut(), dto.heureFin(), dto.siteId(), saved.isPublie()
        );

        return saved;
    }

    /**
     * V33-01/02 SEC : validate a creneau update payload against business rules.
     * Throws BusinessRuleException (mapped to HTTP 422 by GlobalExceptionHandler) on any violation.
     */
    private void validateCreneauUpdate(String orgId, String creneauId, CreneauAssigneDto dto) {
        // 1. Time sanity
        if (dto.heureDebut() < 0 || dto.heureFin() > 24 || dto.heureFin() <= dto.heureDebut()) {
            throw new BusinessRuleException(
                "Heures invalides : debut " + dto.heureDebut() + ", fin " + dto.heureFin()
                + " (attendu : 0 <= debut < fin <= 24)");
        }
        if ((dto.heureFin() - dto.heureDebut()) > MAX_HEURES_CRENEAU) {
            throw new BusinessRuleException(
                "Creneau trop long : " + (dto.heureFin() - dto.heureDebut())
                + "h (max autorise : " + MAX_HEURES_CRENEAU + "h)");
        }
        if (dto.jour() < 0 || dto.jour() > 6) {
            throw new BusinessRuleException("Jour invalide : " + dto.jour() + " (attendu 0..6)");
        }

        // 2. Cross-org employe ownership : target employe must belong to current org
        com.schedy.entity.Employe targetEmploye = employeRepository
            .findByIdAndOrganisationId(dto.employeId(), orgId)
            .orElseThrow(() -> new BusinessRuleException(
                "Employe " + dto.employeId() + " introuvable dans l'organisation"));

        // 2.bis Sprint 16 / Feature 2 : role compatibility check.
        // If the payload declares a role, it must be one the employee actually holds
        // (anywhere in their hierarchy, primary or secondary).
        if (dto.role() != null && !dto.role().isBlank() && !targetEmploye.hasRole(dto.role())) {
            throw new BusinessRuleException(
                "Role " + dto.role() + " non detenu par cet employe "
                + "(roles : " + (targetEmploye.getRoles() == null ? "aucun" : targetEmploye.getRoles()) + ")");
        }

        // 3. Overlap detection : look for any OTHER creneau on (employe, semaine, jour) that
        //    overlaps the new time range. We exclude the creneau being updated by ID.
        List<CreneauAssigne> sameDay = creneauRepository
            .findByEmployeIdAndSemaineAndOrganisationId(dto.employeId(), dto.semaine(), orgId);
        for (CreneauAssigne existing : sameDay) {
            if (existing.getId().equals(creneauId)) continue;          // skip self
            if (existing.getJour() != dto.jour()) continue;             // different day
            boolean overlap = existing.getHeureDebut() < dto.heureFin()
                           && dto.heureDebut() < existing.getHeureFin();
            if (overlap) {
                throw new BusinessRuleException(
                    "Conflit horaire : un autre creneau existe deja pour cet employe le jour "
                    + dto.jour() + " entre " + existing.getHeureDebut() + "h et "
                    + existing.getHeureFin() + "h");
            }
        }

        // 4. Approved leave conflict : convert (semaine, jour) -> LocalDate, then check any
        //    approved DemandeConge for this employe spanning that date.
        LocalDate targetDate = computeDateFromSemaineEtJour(dto.semaine(), dto.jour());
        List<DemandeConge> approvedConges =
            demandeCongeRepository.findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                orgId, StatutDemande.approuve, targetDate, targetDate);
        boolean hasConge = approvedConges.stream()
            .anyMatch(d -> dto.employeId().equals(d.getEmployeId()));
        if (hasConge) {
            throw new BusinessRuleException(
                "Conflit conge : l'employe a un conge approuve sur le " + targetDate);
        }
    }

    /**
     * Convert an ISO week string ("2026-W15") + jour (0=Monday..6=Sunday) into a LocalDate.
     * Mirrors the helper in AutoAffectationService.getLundiDeSemaine().
     */
    private LocalDate computeDateFromSemaineEtJour(String semaine, int jour) {
        if (semaine == null || !semaine.matches("\\d{4}-W\\d{1,2}")) {
            throw new BusinessRuleException("Format semaine invalide : attendu 'YYYY-WNN', recu : " + semaine);
        }
        try {
            String[] parts = semaine.split("-W");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            if (week < 1 || week > 53) {
                throw new BusinessRuleException("Numero de semaine invalide : " + week);
            }
            LocalDate monday = LocalDate.of(year, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(DayOfWeek.MONDAY);
            return monday.plusDays(jour);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Format semaine invalide : " + semaine);
        }
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        CreneauAssigne creneau = creneauRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", id));
        creneauRepository.delete(creneau);
    }

    @Transactional
    public void deleteBySemaine(String semaine) {
        String orgId = tenantContext.requireOrganisationId();
        creneauRepository.deleteBySemaineAndOrganisationId(semaine, orgId);
    }

    @Transactional
    public void deleteBySemaineAndSite(String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        creneauRepository.deleteBySemaineAndSiteIdAndOrganisationId(semaine, siteId, orgId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // V47 : workflow brouillon → publication
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V47 : publie tous les créneaux brouillons d'une semaine pour l'organisation
     * courante. Si siteId est non null, limite au site concerné.
     *
     * @return nombre de créneaux effectivement passés de brouillon à publié.
     */
    @Transactional
    public int publier(String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        int count = creneauRepository.publierBrouillons(orgId, semaine, siteId);
        log.info("audit.creneau.publier orgId={} semaine={} siteId={} count={}",
                orgId, semaine, siteId, count);
        return count;
    }

    /**
     * V47 : supprime tous les créneaux brouillons d'une semaine pour l'organisation
     * courante. Si siteId est non null, limite au site concerné.
     *
     * @return nombre de créneaux supprimés.
     */
    @Transactional
    public int supprimerBrouillons(String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        int count = creneauRepository.supprimerBrouillons(orgId, semaine, siteId);
        log.info("audit.creneau.supprimerBrouillons orgId={} semaine={} siteId={} count={}",
                orgId, semaine, siteId, count);
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers V47
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V47 : l'utilisateur courant est-il un EMPLOYE (pas ADMIN ni MANAGER) ?
     * Les EMPLOYES ne doivent voir que les créneaux publie=true.
     */
    private boolean isCallerEmployee() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        boolean isAdminOrManager = auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r)
                || "ROLE_MANAGER".equals(r)
                || "ROLE_SUPERADMIN".equals(r);
        });
        if (isAdminOrManager) return false;
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_EMPLOYEE".equals(a.getAuthority()));
    }

    /**
     * V47 : filtre une liste de créneaux selon le rôle de l'appelant.
     * ADMIN/MANAGER : voit tout. EMPLOYE : uniquement publie=true.
     */
    private List<CreneauAssigne> filterPublieForCaller(List<CreneauAssigne> list) {
        if (!isCallerEmployee()) return list;
        return list.stream().filter(CreneauAssigne::isPublie).toList();
    }

    /**
     * V47 : la semaine (format ISO "YYYY-Www") est-elle la semaine courante ou une
     * semaine future ? Utilisé pour décider si un update doit repasser le créneau
     * en brouillon (historique = reste publié).
     */
    private boolean isSemaineCurrentOrFuture(String semaine) {
        if (semaine == null || !semaine.matches("\\d{4}-W\\d{1,2}")) return true;
        try {
            String[] parts = semaine.split("-W");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            LocalDate now = LocalDate.now();
            int currentYear = now.get(WeekFields.ISO.weekBasedYear());
            int currentWeek = now.get(WeekFields.ISO.weekOfWeekBasedYear());
            if (year > currentYear) return true;
            if (year < currentYear) return false;
            return week >= currentWeek;
        } catch (NumberFormatException e) {
            return true; // on doute → on remet en brouillon (fail-safe)
        }
    }
}

package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.entity.*;
import com.schedy.repository.*;
import com.schedy.service.affectation.AffectationSolver;
import com.schedy.service.affectation.ContexteAffectation;
import com.schedy.service.affectation.SolverResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schedy.exception.BusinessRuleException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AutoAffectationService {

    private final CreneauAssigneRepository creneauRepository;
    private final EmployeRepository employeRepository;
    private final ExigenceRepository exigenceRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final JourFerieRepository jourFerieRepository;
    private final ParametresRepository parametresRepository;
    private final TenantContext tenantContext;
    private final AffectationSolver solver;

    @Transactional
    public AutoAffectationResult autoAffecter(String semaine, String siteId, boolean forceReplace) {
        String orgId = tenantContext.requireOrganisationId();

        // ── Force replace: delete existing creneaux for this week/site ──
        if (forceReplace) {
            if (siteId != null) {
                creneauRepository.deleteBySemaineAndSiteIdAndOrganisationId(semaine, siteId, orgId);
            } else {
                creneauRepository.deleteBySemaineAndOrganisationId(semaine, orgId);
            }
        }

        // ── Load all data ──────────────────────────────────────
        List<Exigence> exigences = loadExigences(orgId, siteId);
        List<Employe> employes = loadEmployes(orgId, siteId);
        List<CreneauAssigne> creneauxExistants = loadCreneaux(orgId, semaine);
        LocalDate lundi = getLundiDeSemaine(semaine);
        List<DemandeConge> congesApprouves = loadCongesApprouves(orgId, lundi);
        List<JourFerie> joursFeries = jourFerieRepository.findByOrganisationId(orgId);
        Parametres parametres = loadParametres(orgId, siteId);

        // ── Build employeParId index ───────────────────────────
        Map<String, Employe> employeParId = employes.stream()
                .collect(Collectors.toMap(Employe::getId, e -> e));

        // ── Build context record ───────────────────────────────
        ContexteAffectation ctx = new ContexteAffectation(
                exigences,
                employes,
                employeParId,
                creneauxExistants,
                congesApprouves,
                joursFeries,
                parametres.getDureeMinAffectation(),
                parametres.getPlanningGranularite(),
                parametres.getReglesAffectation(),
                parametres.getHeuresMaxSemaine(),
                parametres.getDureeMaxJour() != null ? parametres.getDureeMaxJour() : 10.0,
                parametres.getReposMinEntreShifts() != null ? parametres.getReposMinEntreShifts() : 0.0,
                parametres.getReposHebdoMin() != null ? parametres.getReposHebdoMin() : 0.0,
                parametres.getMaxJoursConsecutifs() != null ? parametres.getMaxJoursConsecutifs() : 0,
                parametres.getPauseFixeHeureDebut(),
                parametres.getPauseFixeHeureFin(),
                parametres.getPauseFixeJours() != null ? parametres.getPauseFixeJours() : List.of(),
                lundi,
                semaine,
                siteId,
                orgId
        );

        // ── Delegate to solver ────────────────────────────────
        SolverResult result = solver.resoudre(ctx);

        // ── Batch-persist new creneaux ─────────────────────────
        // Défense contre la contrainte unique (V28) : on dédup par tuple exact
        // (employé, semaine, jour, site, heures) pour éviter qu'un doublon
        // produit par le solveur fasse éclater la transaction.
        Map<String, CreneauAssigne> deduped = new LinkedHashMap<>();
        for (CreneauAssigne c : result.nouveauxCreneaux()) {
            String key = c.getOrganisationId() + "|" + c.getEmployeId() + "|" + c.getSemaine()
                    + "|" + c.getJour() + "|" + c.getSiteId()
                    + "|" + c.getHeureDebut() + "|" + c.getHeureFin();
            deduped.putIfAbsent(key, c);
        }
        List<CreneauAssigne> persisted = creneauRepository.saveAll(deduped.values());

        // ── Return existing + newly saved creneaux ─────────────
        List<CreneauAssigne> tous = new ArrayList<>(creneauxExistants.size() + persisted.size());
        tous.addAll(creneauxExistants);
        tous.addAll(persisted);

        return new AutoAffectationResult(result.totalAffectes(), tous);
    }

    // ── Data loading ───────────────────────────────────────────

    private List<Exigence> loadExigences(String orgId, String siteId) {
        if (siteId != null) {
            return exigenceRepository.findBySiteIdAndOrganisationId(siteId, orgId);
        }
        return exigenceRepository.findByOrganisationId(orgId);
    }

    private List<Employe> loadEmployes(String orgId, String siteId) {
        if (siteId != null) {
            return employeRepository.findBySiteIdsContainingAndOrganisationId(siteId, orgId);
        }
        return employeRepository.findByOrganisationId(orgId);
    }

    private List<CreneauAssigne> loadCreneaux(String orgId, String semaine) {
        return creneauRepository.findBySemaineAndOrganisationId(semaine, orgId);
    }

    /**
     * Returns only the approved leaves that overlap with the target week,
     * delegating the date-range filter to the database instead of loading
     * the full organisation history and filtering in Java.
     *
     * Overlap condition: dateFin >= lundi AND dateDebut <= dimanche.
     */
    private List<DemandeConge> loadCongesApprouves(String orgId, LocalDate lundi) {
        LocalDate dimanche = lundi.plusDays(6);
        return demandeCongeRepository
                .findByOrganisationIdAndStatutAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        orgId, StatutDemande.approuve, lundi, dimanche);
    }

    private Parametres loadParametres(String orgId, String siteId) {
        // 1. Try site-level parametres
        if (siteId != null) {
            Optional<Parametres> siteParams =
                    parametresRepository.findBySiteIdAndOrganisationId(siteId, orgId);
            if (siteParams.isPresent()) return siteParams.get();
        }
        // 2. Fallback to org-level parametres (siteId IS NULL)
        return parametresRepository.findBySiteIdIsNullAndOrganisationId(orgId)
                .orElseGet(() -> Parametres.builder()
                        .dureeMinAffectation(1.0)
                        .heuresMaxSemaine(48.0)
                        .reglesAffectation(List.of("disponibilite", "equite"))
                        .build());
    }

    // ── Week date utilities ────────────────────────────────────

    private LocalDate getLundiDeSemaine(String semaine) {
        if (semaine == null || !semaine.matches("\\d{4}-W\\d{1,2}")) {
            throw new BusinessRuleException("Format semaine invalide: attendu 'YYYY-WNN', recu: " + semaine);
        }
        try {
            String[] parts = semaine.split("-W");
            int year = Integer.parseInt(parts[0]);
            int week = Integer.parseInt(parts[1]);
            if (week < 1 || week > 53) {
                throw new BusinessRuleException("Numero de semaine invalide: " + week);
            }
            return LocalDate.of(year, 1, 4)
                    .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                    .with(DayOfWeek.MONDAY);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Format semaine invalide: " + semaine);
        }
    }

    // ── Result record ──────────────────────────────────────────

    public record AutoAffectationResult(int totalAffectes, List<CreneauAssigne> creneaux) {}
}

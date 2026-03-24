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
    public AutoAffectationResult autoAffecter(String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();

        // ── Load all data ──────────────────────────────────────
        List<Exigence> exigences = loadExigences(orgId, siteId);
        List<Employe> employes = loadEmployes(orgId, siteId);
        List<CreneauAssigne> creneauxExistants = loadCreneaux(orgId, semaine);
        List<DemandeConge> congesApprouves = loadCongesApprouves(orgId);
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
                getLundiDeSemaine(semaine),
                semaine,
                siteId,
                orgId
        );

        // ── Delegate to solver ────────────────────────────────
        SolverResult result = solver.resoudre(ctx);

        // ── Batch-persist new creneaux ─────────────────────────
        List<CreneauAssigne> persisted = creneauRepository.saveAll(result.nouveauxCreneaux());

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

    private List<DemandeConge> loadCongesApprouves(String orgId) {
        return demandeCongeRepository.findByOrganisationIdAndStatut(orgId, StatutDemande.approuve);
    }

    private Parametres loadParametres(String orgId, String siteId) {
        Optional<Parametres> params;
        if (siteId != null) {
            params = parametresRepository.findBySiteIdAndOrganisationId(siteId, orgId);
        } else {
            params = parametresRepository.findBySiteIdIsNullAndOrganisationId(orgId);
        }
        return params.orElseGet(() -> Parametres.builder()
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

package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.entity.*;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplacementService {

    private static final double EPSILON = 1e-9;

    static final int POIDS_DISPONIBILITE = 30;
    static final int POIDS_MEME_ROLE = 25;
    static final int POIDS_EQUITE_HEURES = 20;
    static final int POIDS_MEME_SITE = 15;
    static final int POIDS_FIABILITE = 10;

    private final EmployeRepository employeRepo;
    private final CreneauAssigneRepository creneauRepo;
    private final DemandeCongeRepository demandeCongeRepo;
    private final AbsenceImprevueRepository absenceRepo;
    private final JourFerieRepository jourFerieRepo;
    private final ParametresRepository parametresRepo;
    private final TenantContext tenantContext;

    @Transactional(readOnly = true)
    public List<RemplacantDto> findReplacements(String creneauId) {
        String orgId = tenantContext.requireOrganisationId();

        CreneauAssigne creneau = creneauRepo.findByIdAndOrganisationId(creneauId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", creneauId));

        LocalDate dateCreneau = getDateFromCreneau(creneau.getJour(), creneau.getSemaine());
        String semaine = creneau.getSemaine();

        // Charger toutes les données nécessaires en amont (open-in-view=false)
        List<Employe> tousEmployes = employeRepo.findByOrganisationId(orgId);

        // Récupérer le rôle de l'employé absent pour scorer les remplaçants par correspondance de rôle
        String absentRole = tousEmployes.stream()
                .filter(e -> e.getId().equals(creneau.getEmployeId()))
                .map(Employe::getRole)
                .findFirst()
                .orElse(null);

        // Exclure le créneau de l'absent de la liste des créneaux actifs :
        // sans cette exclusion, si un candidat avait déjà été réassigné sur ce créneau
        // lors d'une précédente tentative (données incohérentes), ou si l'on recalcule
        // après une mutation, ce créneau pourrait créer un faux conflit cross-site.
        // C'est aussi une défense en profondeur contre toute réassignation partielle.
        final String creneauExcluId = creneau.getId();
        List<CreneauAssigne> tousCreneaux = creneauRepo.findBySemaineAndOrganisationId(semaine, orgId)
                .stream()
                .filter(c -> !c.getId().equals(creneauExcluId))
                .collect(Collectors.toList());
        List<DemandeConge> congesApprouves = demandeCongeRepo
                .findByOrganisationIdAndStatut(orgId, StatutDemande.approuve);
        List<JourFerie> feries = jourFerieRepo.findByOrganisationId(orgId);
        Set<String> absentsDuJour = absenceRepo
                .findByOrganisationIdAndDateAbsence(orgId, dateCreneau)
                .stream()
                .filter(a -> a.getStatut() != StatutAbsenceImprevue.ANNULEE
                        && a.getStatut() != StatutAbsenceImprevue.REFUSEE)
                .map(AbsenceImprevue::getEmployeId)
                .collect(Collectors.toSet());

        Parametres params = loadParametres(orgId, creneau.getSiteId());
        double granularite = params.getPlanningGranularite();
        double heuresMax = params.getHeuresMaxSemaine() != null ? params.getHeuresMaxSemaine() : 48.0;

        // Exclure l'employé absent
        absentsDuJour.add(creneau.getEmployeId());

        // Statistiques pour l'équité
        DoubleSummaryStatistics heuresStats = tousCreneaux.stream()
                .map(CreneauAssigne::getEmployeId)
                .distinct()
                .mapToDouble(id -> getHeuresSemaine(tousCreneaux, id, semaine))
                .summaryStatistics();

        // Check if this day is a public holiday for the creneau's site
        boolean estFerie = estJourFerie(feries, dateCreneau, creneau.getSiteId());
        if (estFerie) {
            log.info("Replacement search skipped: {} is a public holiday for site {}", dateCreneau, creneau.getSiteId());
            return List.of();
        }

        return tousEmployes.stream()
                .filter(emp -> !absentsDuJour.contains(emp.getId()))
                // Site membership: employee must belong to the creneau's site
                .filter(emp -> emp.getSiteIds() != null
                        && emp.getSiteIds().contains(creneau.getSiteId()))
                // Leave check with hour-level overlap
                .filter(emp -> !estEnConge(congesApprouves, emp.getId(), dateCreneau,
                        creneau.getHeureDebut(), creneau.getHeureFin()))
                .filter(emp -> !aConflitCrossSite(emp.getId(), creneau.getJour(),
                        creneau.getHeureDebut(), creneau.getHeureFin(), tousCreneaux, semaine))
                .filter(emp -> {
                    double heuresActuelles = getHeuresSemaine(tousCreneaux, emp.getId(), semaine);
                    double dureeCreneau = creneau.getHeureFin() - creneau.getHeureDebut();
                    return heuresActuelles + dureeCreneau <= heuresMax + EPSILON;
                })
                .filter(emp -> estDisponiblePlage(emp, creneau.getJour(),
                        creneau.getHeureDebut(), creneau.getHeureFin(), granularite))
                .map(emp -> buildDto(emp, creneau, semaine, tousCreneaux, heuresStats, heuresMax, absentRole))
                .sorted(Comparator.comparingInt(RemplacantDto::score).reversed()
                        .thenComparingDouble(RemplacantDto::heuresSemaine))
                .toList();
    }

    private RemplacantDto buildDto(Employe emp, CreneauAssigne creneau, String semaine,
                                    List<CreneauAssigne> tousCreneaux,
                                    DoubleSummaryStatistics heuresStats, double heuresMax,
                                    String absentRole) {
        double heuresSemaine = getHeuresSemaine(tousCreneaux, emp.getId(), semaine);
        int score = computeScore(emp, creneau, heuresSemaine, heuresStats, heuresMax, absentRole);

        List<String> obstacles = new ArrayList<>();
        double dureeCreneau = creneau.getHeureFin() - creneau.getHeureDebut();
        if (heuresSemaine + dureeCreneau > heuresMax * 0.9) {
            obstacles.add("heures_max_proches");
        }

        return new RemplacantDto(
                emp.getId(),
                emp.getNom(),
                emp.getRole(),
                emp.getTelephone(),
                score,
                heuresSemaine,
                emp.getSiteIds() != null && emp.getSiteIds().contains(creneau.getSiteId()),
                obstacles
        );
    }

    private int computeScore(Employe emp, CreneauAssigne creneau,
                              double heuresEmp, DoubleSummaryStatistics heuresStats,
                              double heuresMax, String absentRole) {
        int score = 0;

        // Disponibilité (30 pts) — déjà filtré, donc 30 pts
        score += POIDS_DISPONIBILITE;

        // Rôle (25 pts max) — comparaison avec le rôle de l'employé absent
        // 25 pts si correspondance exacte, 12 pts si le candidat a un rôle (sans correspondance)
        if (absentRole != null && !absentRole.isBlank() && absentRole.equals(emp.getRole())) {
            score += POIDS_MEME_ROLE;  // 25 pts for exact role match
        } else if (emp.getRole() != null && !emp.getRole().isBlank()) {
            score += POIDS_MEME_ROLE / 2;  // 12 pts partial credit
        }

        // Équité heures (20 pts) — moins d'heures = plus de points
        double range = heuresStats.getMax() - heuresStats.getMin();
        if (range > EPSILON) {
            double ratio = (heuresEmp - heuresStats.getMin()) / range;
            score += (int) Math.round(POIDS_EQUITE_HEURES * (1.0 - ratio));
        } else {
            score += POIDS_EQUITE_HEURES;
        }

        // Même site (15 pts)
        if (emp.getSiteIds() != null && emp.getSiteIds().contains(creneau.getSiteId())) {
            score += POIDS_MEME_SITE;
        } else if (emp.getSiteIds() != null && !emp.getSiteIds().isEmpty()) {
            score += POIDS_MEME_SITE / 2;
        }

        // Fiabilité (10 pts) — pour l'instant 10 pts (historique à implémenter V2)
        score += POIDS_FIABILITE;

        return Math.min(100, Math.max(0, score));
    }

    // ── Contraintes réutilisées du GreedySolver ──────────────────

    private boolean estEnConge(List<DemandeConge> conges, String employeId,
                                LocalDate date, double slotDebut, double slotFin) {
        return conges.stream().anyMatch(d -> {
            if (!d.getEmployeId().equals(employeId)) return false;
            if (date.isBefore(d.getDateDebut()) || date.isAfter(d.getDateFin())) return false;

            // No hour precision → full-day leave
            if (d.getHeureDebut() == null || d.getHeureFin() == null) return true;

            // Hour-scoped leave: compute the leave window for this specific day
            double leaveStart = date.equals(d.getDateDebut()) ? d.getHeureDebut() : 0.0;
            double leaveEnd   = date.equals(d.getDateFin())   ? d.getHeureFin()   : 24.0;

            // Overlap: slot [slotDebut, slotFin[ and leave [leaveStart, leaveEnd[
            return slotDebut < leaveEnd - EPSILON && leaveStart < slotFin - EPSILON;
        });
    }

    private boolean estJourFerie(List<JourFerie> feries, LocalDate date, String siteId) {
        return feries.stream()
                .filter(f -> f.getSiteId() == null || f.getSiteId().equals(siteId))
                .anyMatch(f -> {
                    if (f.isRecurrent()) {
                        return f.getDate().getMonthValue() == date.getMonthValue()
                                && f.getDate().getDayOfMonth() == date.getDayOfMonth();
                    }
                    return f.getDate().equals(date);
                });
    }

    private boolean estDisponible(Employe emp, int jour, double temps, double granularite) {
        return emp.getDisponibilites().stream().anyMatch(d ->
                d.getJour() == jour
                        && temps >= d.getHeureDebut() - EPSILON
                        && temps + granularite <= d.getHeureFin() + EPSILON);
    }

    private boolean estDisponiblePlage(Employe emp, int jour,
                                        double heureDebut, double heureFin, double granularite) {
        // Si l'employé n'a aucune disponibilité configurée, on ne filtre pas :
        // une contrainte non renseignée signifie "pas de restriction déclarée".
        if (emp.getDisponibilites() == null || emp.getDisponibilites().isEmpty()) {
            return true;
        }
        // Défense contre une granularité nulle ou négative qui provoquerait une boucle infinie.
        double step = (granularite > EPSILON) ? granularite : 0.5;
        for (double h = heureDebut; h < heureFin - EPSILON; h += step) {
            if (!estDisponible(emp, jour, h, step)) return false;
        }
        return true;
    }

    private boolean aConflitCrossSite(String employeId, int jour,
                                       double heureDebut, double heureFin,
                                       List<CreneauAssigne> creneaux, String semaine) {
        return creneaux.stream()
                .filter(c -> c.getEmployeId().equals(employeId))
                .filter(c -> c.getSemaine().equals(semaine))
                .filter(c -> c.getJour() == jour)
                .anyMatch(c -> heureDebut < c.getHeureFin() - EPSILON
                        && c.getHeureDebut() < heureFin - EPSILON);
    }

    private double getHeuresSemaine(List<CreneauAssigne> creneaux, String employeId, String semaine) {
        return creneaux.stream()
                .filter(c -> c.getEmployeId().equals(employeId))
                .filter(c -> c.getSemaine().equals(semaine))
                .mapToDouble(c -> c.getHeureFin() - c.getHeureDebut())
                .sum();
    }

    private LocalDate getDateFromCreneau(int jour, String semaine) {
        String[] parts = semaine.split("-W");
        int year = Integer.parseInt(parts[0]);
        int week = Integer.parseInt(parts[1]);
        LocalDate lundi = LocalDate.of(year, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(DayOfWeek.MONDAY);
        return lundi.plusDays(jour);
    }

    private Parametres loadParametres(String orgId, String siteId) {
        if (siteId != null) {
            return parametresRepo.findBySiteIdAndOrganisationId(siteId, orgId)
                    .orElseGet(() -> parametresRepo.findBySiteIdIsNullAndOrganisationId(orgId)
                            .orElseGet(() -> Parametres.builder().build()));
        }
        return parametresRepo.findBySiteIdIsNullAndOrganisationId(orgId)
                .orElseGet(() -> Parametres.builder().build());
    }

    // ── DTO ──────────────────────────────────────────────────────

    public record RemplacantDto(
            String employeId,
            String nom,
            String role,
            String telephone,
            int score,
            double heuresSemaine,
            boolean memeSite,
            List<String> obstacles
    ) {}
}

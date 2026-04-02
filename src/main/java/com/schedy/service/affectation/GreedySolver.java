package com.schedy.service.affectation;

import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.Employe;
import com.schedy.entity.Exigence;
import com.schedy.entity.JourFerie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Greedy scheduling solver implementing the MRV (Minimum Remaining Values) heuristic
 * with dynamic re-sorting, cross-site conflict detection, equity-based candidate
 * sorting, and a 2-swap post-processing pass for load balancing.
 *
 * <p>This class is a pure algorithm component: it has no repository or Spring
 * transactional dependencies. All input data arrives via {@link ContexteAffectation}.
 */
@Component
public class GreedySolver implements AffectationSolver {

    private static final Logger log = LoggerFactory.getLogger(GreedySolver.class);

    /** Floating-point epsilon used for granularity-aware double comparisons. */
    private static final double EPSILON = 1e-9;

    // =========================================================================
    // Public entry point
    // =========================================================================

    @Override
    public SolverResult resoudre(ContexteAffectation ctx) {

        // ── 1. Build working state ────────────────────────────────────────────
        // All creneaux (existing + newly created this run) for the whole week,
        // across ALL sites — required for cross-site conflict detection (fix #2)
        // and multi-site equity hour counting (fix #1).
        List<CreneauAssigne> tousLesCreneaux = new ArrayList<>(ctx.creneauxExistants());
        List<CreneauAssigne> nouveauxCreneaux = new ArrayList<>();

        // Pre-compute day index: jour (int 0-6) -> LocalDate (fix #8)
        Map<Integer, LocalDate> dateParJour = construireDateParJour(ctx.lundi());

        // ── 2. Build (exigence, jour) pair list, sorted MRV (fix #9) ─────────
        List<ExigenceJour> paires = construirePaires(ctx.exigences(), ctx.employes());

        // ── 3. Greedy assignment loop ─────────────────────────────────────────
        int totalAffectes = 0;

        for (int pairIdx = 0; pairIdx < paires.size(); pairIdx++) {

            ExigenceJour paire = paires.get(pairIdx);
            Exigence exigence = paire.exigence();
            int jour = paire.jour();

            // Fix #5: holiday short-circuit — evaluate ONCE per day, outside employee loop
            LocalDate dateJour = dateParJour.get(jour);
            if (dateJour == null || estJourFerie(ctx.joursFeries(), dateJour, exigence.getSiteId())) {
                continue;
            }

            // Inner loop: keep assigning until the requirement is met or no progress
            boolean changed = true;
            while (changed) {
                changed = false;

                // Fix #7: index creneaux by day for O(1) lookup within the inner loop
                Map<Integer, List<CreneauAssigne>> creneauxParJour =
                        indexerParJour(tousLesCreneaux, ctx.semaine());

                // Step A: identify missing slots for this (exigence, jour)
                List<Double> heuresManquantes = trouverHeuresManquantes(
                        exigence, jour, creneauxParJour, ctx);

                if (heuresManquantes.isEmpty()) break;

                // Step B: determine which employees already fully cover all missing hours
                // Fix #4: only exclude an employee from candidacy if they already cover
                // ALL missing hours for this requirement — do not exclude partial covers.
                Set<String> dejaPresents = trouverDejaPresentsComplets(
                        exigence, jour, heuresManquantes, creneauxParJour, ctx);

                // Step C: build candidate list
                List<Employe> candidats = filtrerCandidats(
                        exigence, jour, dateJour, dejaPresents, ctx);

                if (candidats.isEmpty()) break;

                // Step D: sort candidates by active rules
                trierCandidats(candidats, ctx.regles(), jour, tousLesCreneaux, ctx);

                // Step E: attempt assignment for the best candidate
                for (Employe emp : candidats) {

                    // Compute coverable hours respecting availability (fix #3: granularity step)
                    List<Double> heuresCouvrables = heuresManquantes.stream()
                            .filter(h -> estDisponible(emp, jour, h, ctx.granularite()))
                            .toList();

                    if (heuresCouvrables.isEmpty()) continue;

                    // Fix #3: group with granularity-aware epsilon comparison
                    List<double[]> plages = grouperConsecutives(heuresCouvrables, ctx.granularite());

                    // Weekly hours remaining budget
                    double heuresActuelles = getHeuresSemaine(
                            tousLesCreneaux, emp.getId(), ctx.semaine());
                    double remaining = ctx.heuresMaxSemaine() - heuresActuelles;

                    // Skip employee entirely if remaining budget < dureeMin
                    if (remaining < ctx.dureeMin() - EPSILON) continue;

                    Optional<double[]> meilleurePlage = plages.stream()
                            .filter(p -> (p[1] - p[0]) >= ctx.dureeMin() - EPSILON)
                            .max(Comparator.comparingDouble(p -> p[1] - p[0]));

                    if (meilleurePlage.isEmpty()) continue;

                    double[] plage = meilleurePlage.get();

                    // Truncate block to fit within remaining weekly budget
                    double dureePlage = plage[1] - plage[0];
                    if (dureePlage > remaining + EPSILON) {
                        plage = new double[]{plage[0], plage[0] + remaining};
                        dureePlage = remaining;
                        // Verify truncated block still meets dureeMin
                        if (dureePlage < ctx.dureeMin() - EPSILON) continue;
                    }

                    // Fix #2: cross-site conflict — employee must not already be assigned
                    // on ANY site during the proposed block
                    if (aConflitCrossSite(emp.getId(), jour, plage[0], plage[1],
                            tousLesCreneaux, ctx.semaine())) {
                        continue;
                    }

                    CreneauAssigne nouveau = CreneauAssigne.builder()
                            .employeId(emp.getId())
                            .jour(jour)
                            .heureDebut(plage[0])
                            .heureFin(plage[1])
                            .semaine(ctx.semaine())
                            .siteId(exigence.getSiteId())
                            .organisationId(ctx.organisationId())
                            .build();

                    tousLesCreneaux.add(nouveau);
                    nouveauxCreneaux.add(nouveau);
                    totalAffectes++;
                    changed = true;

                    // Fix #9: dynamic MRV — re-sort remaining pairs after each assignment
                    if (pairIdx + 1 < paires.size()) {
                        List<ExigenceJour> restantes = new ArrayList<>(
                                paires.subList(pairIdx + 1, paires.size()));
                        restantes.sort(comparateurMrv(ctx.employes(), tousLesCreneaux, ctx));
                        for (int i = 0; i < restantes.size(); i++) {
                            paires.set(pairIdx + 1 + i, restantes.get(i));
                        }
                    }

                    break; // restart inner while with updated state
                }
            }
        }

        // ── 4. Post-processing: 2-swap equity pass (fix #11) ─────────────────
        appliquer2Swap(nouveauxCreneaux, tousLesCreneaux, ctx, dateParJour);

        return new SolverResult(totalAffectes, nouveauxCreneaux);
    }

    // =========================================================================
    // Phase helpers
    // =========================================================================

    /**
     * Builds the list of (exigence, jour) pairs and sorts them by MRV ratio
     * (candidats available / slots required). Pairs with fewer candidates relative
     * to demand are scheduled first.
     */
    private List<ExigenceJour> construirePaires(List<Exigence> exigences,
                                                 List<Employe> employes) {
        List<ExigenceJour> paires = new ArrayList<>();
        for (Exigence ex : exigences) {
            for (int jour : ex.getJours()) {
                paires.add(new ExigenceJour(ex, jour));
            }
        }
        paires.sort(comparateurMrvInitial(employes));
        return paires;
    }

    /**
     * Comparator for initial MRV sort: ascending ratio (candidats / nombreRequis).
     * Lower ratio = more constrained = scheduled first.
     */
    private Comparator<ExigenceJour> comparateurMrvInitial(List<Employe> employes) {
        return Comparator.comparingDouble(paire -> {
            long candidats = employes.stream()
                    .filter(emp -> Objects.equals(emp.getRole(), paire.exigence().getRole()))
                    .count();
            int requis = paire.exigence().getNombreRequis();
            return requis == 0 ? Double.MAX_VALUE : (double) candidats / requis;
        });
    }

    /**
     * Comparator for dynamic MRV re-sort during the greedy loop.
     * Takes currently available candidates (excluding those already blocked) into account.
     */
    private Comparator<ExigenceJour> comparateurMrv(List<Employe> employes,
                                                      List<CreneauAssigne> creneaux,
                                                      ContexteAffectation ctx) {
        return Comparator.comparingDouble(paire -> {
            // Count role-matching employees who have availability on this day
            long candidats = employes.stream()
                    .filter(emp -> Objects.equals(emp.getRole(), paire.exigence().getRole()))
                    .filter(emp -> aDisponibiliteJour(emp, paire.jour()))
                    .count();
            int requis = paire.exigence().getNombreRequis();
            return requis == 0 ? Double.MAX_VALUE : (double) candidats / requis;
        });
    }

    /**
     * Identifies which hour slots within the exigence range are not yet covered
     * by the required number of employees with the correct role.
     * Iterates with {@code ctx.granularite()} step (fix #3).
     */
    private List<Double> trouverHeuresManquantes(Exigence exigence, int jour,
                                                   Map<Integer, List<CreneauAssigne>> creneauxParJour,
                                                   ContexteAffectation ctx) {
        List<Double> manquantes = new ArrayList<>();
        List<CreneauAssigne> creneauxJour =
                creneauxParJour.getOrDefault(jour, List.of());

        for (double h = exigence.getHeureDebut();
             h < exigence.getHeureFin() - EPSILON;
             h += ctx.granularite()) {

            final double slot = h;
            long countRole = creneauxJour.stream()
                    .filter(c -> c.getSiteId().equals(exigence.getSiteId()))
                    .filter(c -> slot >= c.getHeureDebut() - EPSILON
                              && slot < c.getHeureFin() - EPSILON)
                    .map(c -> ctx.employeParId().get(c.getEmployeId()))
                    .filter(emp -> emp != null
                            && Objects.equals(emp.getRole(), exigence.getRole()))
                    .count();

            if (countRole < exigence.getNombreRequis()) {
                manquantes.add(h);
            }
        }
        return manquantes;
    }

    /**
     * Fix #4: builds the set of employee IDs that already cover ALL missing slots
     * for this (exigence, jour) pair. Only fully covering employees are excluded
     * from the candidate pool — partial covers are still eligible.
     */
    private Set<String> trouverDejaPresentsComplets(Exigence exigence, int jour,
                                                      List<Double> heuresManquantes,
                                                      Map<Integer, List<CreneauAssigne>> creneauxParJour,
                                                      ContexteAffectation ctx) {
        List<CreneauAssigne> creneauxJour =
                creneauxParJour.getOrDefault(jour, List.of());

        // Collect all employees of the right role already assigned on the site this day
        Set<String> candidatsAssignes = new HashSet<>();
        for (CreneauAssigne c : creneauxJour) {
            if (!c.getSiteId().equals(exigence.getSiteId())) continue;
            Employe emp = ctx.employeParId().get(c.getEmployeId());
            if (emp != null && Objects.equals(emp.getRole(), exigence.getRole())) {
                candidatsAssignes.add(emp.getId());
            }
        }

        // Among those, keep only the ones that cover every missing slot
        Set<String> couvrantTout = new HashSet<>();
        for (String empId : candidatsAssignes) {
            boolean couvreAll = true;
            for (double slot : heuresManquantes) {
                boolean coveredByEmp = creneauxJour.stream()
                        .filter(c -> c.getEmployeId().equals(empId))
                        .filter(c -> c.getSiteId().equals(exigence.getSiteId()))
                        .anyMatch(c -> slot >= c.getHeureDebut() - EPSILON
                                    && slot < c.getHeureFin() - EPSILON);
                if (!coveredByEmp) {
                    couvreAll = false;
                    break;
                }
            }
            if (couvreAll) {
                couvrantTout.add(empId);
            }
        }
        return couvrantTout;
    }

    /**
     * Builds the filtered list of eligible candidates for a given exigence/day.
     * Employees already fully covering all missing hours are excluded (fix #4).
     * Employees on leave or on a public holiday are excluded.
     * Uses {@code ctx.employeParId()} for O(1) lookup (fix #6).
     */
    private List<Employe> filtrerCandidats(Exigence exigence, int jour, LocalDate dateJour,
                                            Set<String> dejaPresents,
                                            ContexteAffectation ctx) {
        return ctx.employes().stream()
                .filter(emp -> Objects.equals(emp.getRole(), exigence.getRole()))
                .filter(emp -> emp.getSiteIds() != null
                        && emp.getSiteIds().contains(exigence.getSiteId()))
                .filter(emp -> !dejaPresents.contains(emp.getId()))
                .filter(emp -> !estEnConge(ctx.congesApprouves(), emp.getId(), dateJour,
                        exigence.getHeureDebut(), exigence.getHeureFin()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Sorts candidates according to the configured rules (disponibilite, equite,
     * anciennete, age).
     * Fix #1: equity uses {@code getHeuresSemaine} which counts hours across ALL sites.
     */
    private void trierCandidats(List<Employe> candidats, List<String> regles,
                                 int jour, List<CreneauAssigne> creneaux,
                                 ContexteAffectation ctx) {

        // Warn about missing dateEmbauche when anciennete is active
        if (regles.contains("anciennete")) {
            for (Employe emp : candidats) {
                if (emp.getDateEmbauche() == null) {
                    log.warn("[Affectation] Employe {} ({}) sans dateEmbauche — sera traite comme le plus junior",
                            emp.getId(), emp.getNom());
                }
            }
        }

        candidats.sort((a, b) -> {
            for (String regle : regles) {
                int diff = 0;
                switch (regle) {
                    case "disponibilite" -> {
                        double dispoA = getDispoJour(a, jour);
                        double dispoB = getDispoJour(b, jour);
                        diff = Double.compare(dispoB, dispoA); // descending
                    }
                    case "equite" -> {
                        // Fix #1: ALL sites — no siteId filter
                        double heuresA = getHeuresSemaine(creneaux, a.getId(), ctx.semaine());
                        double heuresB = getHeuresSemaine(creneaux, b.getId(), ctx.semaine());
                        diff = Double.compare(heuresA, heuresB); // ascending (least worked first)
                    }
                    case "anciennete" -> {
                        LocalDate dateA = a.getDateEmbauche() != null ? a.getDateEmbauche() : LocalDate.MAX;
                        LocalDate dateB = b.getDateEmbauche() != null ? b.getDateEmbauche() : LocalDate.MAX;
                        diff = dateA.compareTo(dateB); // ascending (most senior first)
                    }
                    case "age" -> {
                        LocalDate dateA = a.getDateNaissance() != null ? a.getDateNaissance() : LocalDate.MAX;
                        LocalDate dateB = b.getDateNaissance() != null ? b.getDateNaissance() : LocalDate.MAX;
                        diff = dateA.compareTo(dateB); // ascending (oldest first)
                    }
                    default -> { /* unknown rule: no effect */ }
                }
                if (diff != 0) return diff;
            }
            // Deterministic tiebreaker: stable ordering by ID when all rules are equal
            return a.getId().compareTo(b.getId());
        });
    }

    // =========================================================================
    // 2-swap post-processing (fix #11)
    // =========================================================================

    /**
     * Attempts to improve equity by iteratively moving a creneau from the employee
     * with the most weekly hours to the one with the fewest (same role).
     * Runs until no improvement is found or {@code MAX_SWAP_ITERATIONS} is reached.
     *
     * <p>Only newly created creneaux are candidates for the swap to avoid disturbing
     * manually-set existing assignments.
     *
     * <p>The 2-swap is SKIPPED when non-equity priority rules (anciennete, age) are
     * active, because swapping based on hours alone would violate the user's
     * configured priority ordering.
     */
    private void appliquer2Swap(List<CreneauAssigne> nouveauxCreneaux,
                                  List<CreneauAssigne> tousLesCreneaux,
                                  ContexteAffectation ctx,
                                  Map<Integer, LocalDate> dateParJour) {

        // Skip 2-swap when non-equity priority rules are active — swapping would
        // destroy the ordering established by anciennete/age/disponibilite rules.
        boolean hasNonEquityRules = ctx.regles().stream()
                .anyMatch(r -> !r.equals("equite"));
        if (hasNonEquityRules) return;

        final int MAX_SWAP_ITERATIONS = 100;

        for (int iter = 0; iter < MAX_SWAP_ITERATIONS; iter++) {
            boolean improved = false;

            // Group employees by role
            Map<String, List<Employe>> parRole = new HashMap<>();
            for (Employe emp : ctx.employes()) {
                String role = emp.getRole() != null ? emp.getRole() : "";
                parRole.computeIfAbsent(role, k -> new ArrayList<>()).add(emp);
            }

            for (List<Employe> groupe : parRole.values()) {
                if (groupe.size() < 2) continue;

                // Find max and min hours employees within this role group
                Employe empMax = groupe.stream()
                        .max(Comparator.comparingDouble(
                                e -> getHeuresSemaine(tousLesCreneaux, e.getId(), ctx.semaine())))
                        .orElse(null);
                Employe empMin = groupe.stream()
                        .min(Comparator.comparingDouble(
                                e -> getHeuresSemaine(tousLesCreneaux, e.getId(), ctx.semaine())))
                        .orElse(null);

                if (empMax == null || empMin == null || empMax.getId().equals(empMin.getId())) {
                    continue;
                }

                double heuresMax = getHeuresSemaine(tousLesCreneaux, empMax.getId(), ctx.semaine());
                double heuresMin = getHeuresSemaine(tousLesCreneaux, empMin.getId(), ctx.semaine());

                // Only bother swapping if the imbalance is larger than one granularity unit
                if (heuresMax - heuresMin < ctx.granularite() + EPSILON) continue;

                // Try to find a newly created creneau belonging to empMax that empMin could absorb
                for (CreneauAssigne creneau : nouveauxCreneaux) {
                    if (!creneau.getEmployeId().equals(empMax.getId())) continue;

                    int jour = creneau.getJour();
                    LocalDate dateJour = dateParJour.get(jour);
                    if (dateJour == null) continue;

                    // empMin must belong to the site of the creneau
                    if (empMin.getSiteIds() == null
                            || !empMin.getSiteIds().contains(creneau.getSiteId())) {
                        continue;
                    }

                    // empMin must be available during this entire block
                    if (!estDisponiblePlage(empMin, jour,
                            creneau.getHeureDebut(), creneau.getHeureFin(),
                            ctx.granularite())) {
                        continue;
                    }

                    // empMin must not be on leave
                    if (estEnConge(ctx.congesApprouves(), empMin.getId(), dateJour,
                            creneau.getHeureDebut(), creneau.getHeureFin())) {
                        continue;
                    }

                    // empMin must not have a cross-site conflict
                    if (aConflitCrossSite(empMin.getId(), jour,
                            creneau.getHeureDebut(), creneau.getHeureFin(),
                            tousLesCreneaux, ctx.semaine())) {
                        continue;
                    }

                    // empMin must not exceed weekly hours cap
                    double heuresMinActuelles = getHeuresSemaine(
                            tousLesCreneaux, empMin.getId(), ctx.semaine());
                    double dureeCreneau = creneau.getHeureFin() - creneau.getHeureDebut();
                    if (heuresMinActuelles + dureeCreneau > ctx.heuresMaxSemaine() + EPSILON) {
                        continue;
                    }

                    // Perform the swap: reassign the creneau from empMax to empMin
                    creneau.setEmployeId(empMin.getId());
                    improved = true;
                    break;
                }

                if (improved) break; // restart outer loop with updated state
            }

            if (!improved) break;
        }
    }

    // =========================================================================
    // Constraint checkers
    // =========================================================================

    /**
     * Returns true if the given date is a public holiday for the given site.
     * Holidays with {@code siteId == null} apply to ALL sites (org-wide).
     * Supports recurrent holidays (same month/day regardless of year).
     */
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

    /**
     * Returns true if the employee has an approved leave overlapping the proposed
     * slot [slotDebut, slotFin[ on the given date.
     *
     * <p>For full-day leaves (no hour precision), the entire day is blocked.
     * For hour-scoped leaves, the actual leave window is computed per day:
     * on the start day the leave begins at heureDebut, on the end day it ends
     * at heureFin, and on interior days the full day (0-24) is blocked.
     */
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

    /**
     * Returns true if the employee has a disponibilite slot covering the given
     * time slot using granularity-aware step (fix #3).
     */
    private boolean estDisponible(Employe emp, int jour, double temps, double granularite) {
        return emp.getDisponibilites().stream().anyMatch(d ->
                d.getJour() == jour
                        && temps >= d.getHeureDebut() - EPSILON
                        && temps + granularite <= d.getHeureFin() + EPSILON);
    }

    /**
     * Returns true if the employee is available for the entire contiguous block
     * [heureDebut, heureFin[.
     */
    private boolean estDisponiblePlage(Employe emp, int jour,
                                        double heureDebut, double heureFin,
                                        double granularite) {
        for (double h = heureDebut; h < heureFin - EPSILON; h += granularite) {
            if (!estDisponible(emp, jour, h, granularite)) return false;
        }
        return true;
    }

    /**
     * Fix #2: cross-site conflict check — returns true if the employee is already
     * assigned on ANY site during [heureDebut, heureFin[ on the given day and week.
     */
    private boolean aConflitCrossSite(String employeId, int jour,
                                       double heureDebut, double heureFin,
                                       List<CreneauAssigne> tousLesCreneaux,
                                       String semaine) {
        return tousLesCreneaux.stream()
                .filter(c -> c.getEmployeId().equals(employeId))
                .filter(c -> c.getSemaine().equals(semaine))
                .filter(c -> c.getJour() == jour)
                // Overlap condition: intervals [a,b[ and [c,d[ overlap iff a < d && c < b
                .anyMatch(c -> heureDebut < c.getHeureFin() - EPSILON
                            && c.getHeureDebut() < heureFin - EPSILON);
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    /**
     * Fix #1 + #10: counts the employee's total weekly hours across ALL sites.
     * No siteId filter is applied.
     */
    private double getHeuresSemaine(List<CreneauAssigne> creneaux,
                                     String employeId, String semaine) {
        return creneaux.stream()
                .filter(c -> c.getEmployeId().equals(employeId))
                .filter(c -> c.getSemaine().equals(semaine))
                .mapToDouble(c -> c.getHeureFin() - c.getHeureDebut())
                .sum();
    }

    /**
     * Returns the total disponibilite duration for the employee on the given day.
     */
    private double getDispoJour(Employe emp, int jour) {
        return emp.getDisponibilites().stream()
                .filter(d -> d.getJour() == jour)
                .mapToDouble(d -> d.getHeureFin() - d.getHeureDebut())
                .sum();
    }

    /**
     * Returns true if the employee has any declared disponibilite on the given day.
     */
    private boolean aDisponibiliteJour(Employe emp, int jour) {
        return emp.getDisponibilites().stream()
                .anyMatch(d -> d.getJour() == jour);
    }

    // =========================================================================
    // Grouping utility
    // =========================================================================

    /**
     * Groups a sorted list of slot start times into contiguous [debut, fin] blocks.
     * Fix #3: uses {@code granularite} as the step size and epsilon for double
     * comparison instead of integer equality.
     *
     * @param heures     sorted list of slot start times (ascending)
     * @param granularite slot width (e.g. 0.5 for 30-minute slots)
     * @return list of [heureDebut, heureFin] arrays
     */
    private List<double[]> grouperConsecutives(List<Double> heures, double granularite) {
        if (heures.isEmpty()) return List.of();

        List<double[]> plages = new ArrayList<>();
        double debut = heures.get(0);
        double fin   = heures.get(0) + granularite;

        for (int i = 1; i < heures.size(); i++) {
            double prochainAttendu = fin;
            double prochainReel    = heures.get(i);
            if (Math.abs(prochainReel - prochainAttendu) < EPSILON) {
                // Consecutive slot: extend the current block
                fin = prochainReel + granularite;
            } else {
                plages.add(new double[]{debut, fin});
                debut = prochainReel;
                fin   = prochainReel + granularite;
            }
        }
        plages.add(new double[]{debut, fin});
        return plages;
    }

    // =========================================================================
    // Index helpers
    // =========================================================================

    /**
     * Fix #7: builds a Map<jour, List<CreneauAssigne>> index for creneaux
     * belonging to the given week. Enables O(1) day lookup instead of full scans.
     */
    private Map<Integer, List<CreneauAssigne>> indexerParJour(List<CreneauAssigne> creneaux,
                                                               String semaine) {
        Map<Integer, List<CreneauAssigne>> index = new HashMap<>();
        for (CreneauAssigne c : creneaux) {
            if (c.getSemaine().equals(semaine)) {
                index.computeIfAbsent(c.getJour(), k -> new ArrayList<>()).add(c);
            }
        }
        return index;
    }

    /**
     * Fix #8: pre-computes jour (0 = Monday … 6 = Sunday) -> LocalDate for the week.
     */
    private Map<Integer, LocalDate> construireDateParJour(LocalDate lundi) {
        Map<Integer, LocalDate> map = new HashMap<>();
        for (int jour = 0; jour <= 6; jour++) {
            map.put(jour, lundi.plusDays(jour));
        }
        return map;
    }

    // =========================================================================
    // Internal records
    // =========================================================================

    /**
     * Lightweight pair coupling an {@link Exigence} with a specific day index.
     * Used as the unit of work for the MRV-sorted processing queue.
     */
    private record ExigenceJour(Exigence exigence, int jour) {}
}

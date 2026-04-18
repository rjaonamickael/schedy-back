package com.schedy.service.affectation;

import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.Employe;
import com.schedy.entity.Exigence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.schedy.service.affectation.SchedulingConstraints.*;

/**
 * Greedy scheduling solver implementing the MRV (Minimum Remaining Values) heuristic
 * with dynamic re-sorting, cross-site conflict detection, equity-based candidate
 * sorting, and a 2-swap post-processing pass for load balancing.
 *
 * <p>Constraint checks and metrics are delegated to {@link SchedulingConstraints}.
 * Post-processing (merge, logging) is delegated to {@link CreneauPostProcessor}.
 *
 * <p>This class is a pure algorithm component: it has no repository or Spring
 * transactional dependencies. All input data arrives via {@link ContexteAffectation}.
 */
@Component
public class GreedySolver implements AffectationSolver {

    private static final Logger log = LoggerFactory.getLogger(GreedySolver.class);

    private static final int MAX_TOTAL_ASSIGNMENTS = 5000;
    private static final long MAX_SOLVER_DURATION_MS = 30_000;

    // =========================================================================
    // Public entry point
    // =========================================================================

    @Override
    public SolverResult resoudre(ContexteAffectation ctx) {

        // ── 0. Input validation (fix B2 + B3) ────────────────────────────
        if (ctx.granularite() <= 0) {
            throw new IllegalArgumentException(
                    "granularite must be > 0, got: " + ctx.granularite());
        }
        if (ctx.dureeMin() > ctx.dureeMaxJour() + EPSILON) {
            log.warn("[Affectation] dureeMin ({}) > dureeMaxJour ({}) — aucun bloc ne pourra "
                    + "respecter les deux contraintes simultanement. Aucune affectation ne sera produite.",
                    ctx.dureeMin(), ctx.dureeMaxJour());
        }

        // ── 1. Build working state ───────────────────────────────────────
        List<CreneauAssigne> tousLesCreneaux = new ArrayList<>(ctx.creneauxExistants());
        List<CreneauAssigne> nouveauxCreneaux = new ArrayList<>();
        Map<Integer, LocalDate> dateParJour = construireDateParJour(ctx.lundi());

        // ── 2. Build (exigence, jour) pair list, sorted MRV ─────────────
        List<ExigenceJour> paires = construirePaires(ctx.exigences(), ctx.employes());

        // ── 3. Greedy assignment loop ────────────────────────────────────
        int totalAffectes = 0;
        Map<Integer, List<CreneauAssigne>> creneauxParJour =
                indexerParJour(tousLesCreneaux, ctx.semaine());
        long solverStartMs = System.currentTimeMillis();

        for (int pairIdx = 0; pairIdx < paires.size(); pairIdx++) {

            if (totalAffectes >= MAX_TOTAL_ASSIGNMENTS
                    || System.currentTimeMillis() - solverStartMs > MAX_SOLVER_DURATION_MS) {
                log.warn("Solver safety limit reached: {} assignments in {}ms",
                        totalAffectes, System.currentTimeMillis() - solverStartMs);
                break;
            }

            ExigenceJour paire = paires.get(pairIdx);
            Exigence exigence = paire.exigence();
            int jour = paire.jour();

            LocalDate dateJour = dateParJour.get(jour);
            if (dateJour == null || estJourFerie(ctx.joursFeries(), dateJour, exigence.getSiteId())) {
                continue;
            }

            boolean changed = true;
            while (changed) {
                changed = false;

                List<Double> heuresManquantes = trouverHeuresManquantes(
                        exigence, jour, creneauxParJour, ctx);
                if (heuresManquantes.isEmpty()) break;

                Set<String> dejaPresents = trouverDejaPresentsComplets(
                        exigence, jour, heuresManquantes, creneauxParJour, ctx);

                List<Employe> candidats = filtrerCandidats(
                        exigence, jour, dejaPresents, ctx);
                if (candidats.isEmpty()) break;

                trierCandidats(candidats, ctx.regles(), jour, tousLesCreneaux, ctx);

                for (Employe emp : candidats) {

                    List<Double> heuresCouvrables = heuresManquantes.stream()
                            .filter(h -> estDisponible(emp, jour, h, ctx.granularite()))
                            .filter(h -> !estEnConge(ctx.congesApprouves(), emp.getId(), dateJour,
                                    h, h + ctx.granularite()))
                            .toList();
                    if (heuresCouvrables.isEmpty()) continue;

                    List<double[]> plages = grouperConsecutives(heuresCouvrables, ctx.granularite());

                    double heuresActuelles = getHeuresSemaine(tousLesCreneaux, emp.getId(), ctx.semaine());
                    double remaining = ctx.heuresMaxSemaine() - heuresActuelles;
                    if (remaining < ctx.dureeMin() - EPSILON) continue;

                    double heuresJourActuelles = getHeuresJour(tousLesCreneaux, emp.getId(), jour, ctx.semaine());
                    double remainingJour = ctx.dureeMaxJour() - heuresJourActuelles;
                    if (remainingJour < ctx.dureeMin() - EPSILON) continue;

                    Optional<double[]> meilleurePlage = plages.stream()
                            .filter(p -> (p[1] - p[0]) >= ctx.dureeMin() - EPSILON)
                            .max(Comparator.comparingDouble(p -> p[1] - p[0]));
                    if (meilleurePlage.isEmpty()) continue;

                    double[] plage = meilleurePlage.get();
                    double dureePlage = plage[1] - plage[0];

                    // Truncate to weekly budget
                    if (dureePlage > remaining + EPSILON) {
                        plage = new double[]{plage[0], plage[0] + remaining};
                        dureePlage = remaining;
                        if (dureePlage < ctx.dureeMin() - EPSILON) continue;
                    }

                    // Truncate to daily budget
                    if (dureePlage > remainingJour + EPSILON) {
                        plage = new double[]{plage[0], plage[0] + remainingJour};
                        dureePlage = remainingJour;
                        if (dureePlage < ctx.dureeMin() - EPSILON) continue;
                    }

                    if (aConflitCrossSite(emp.getId(), jour, plage[0], plage[1],
                            tousLesCreneaux, ctx.semaine())) {
                        continue;
                    }

                    // L1: minimum rest between shifts (country-configurable, 0 = disabled)
                    if (aViolationReposEntreShifts(emp.getId(), jour, plage[0], plage[1],
                            tousLesCreneaux, ctx.semaine(), ctx.reposMinEntreShifts())) {
                        continue;
                    }

                    // L2: minimum weekly rest (country-configurable, 0 = disabled)
                    if (aViolationReposHebdo(emp.getId(), jour,
                            tousLesCreneaux, ctx.semaine(), ctx.reposHebdoMin())) {
                        continue;
                    }

                    // L5: max consecutive working days (country-configurable, 0 = disabled)
                    if (depasseMaxJoursConsecutifs(emp.getId(), jour,
                            tousLesCreneaux, ctx.semaine(), ctx.maxJoursConsecutifs())) {
                        continue;
                    }

                    CreneauAssigne nouveau = CreneauAssigne.builder()
                            .employeId(emp.getId())
                            .jour(jour)
                            .heureDebut(plage[0])
                            .heureFin(plage[1])
                            .semaine(ctx.semaine())
                            .siteId(exigence.getSiteId())
                            // Sprint 16 / Feature 2 : capture the role being filled
                            // so the UI can display it and Plan B can match it later.
                            .role(exigence.getRole())
                            .organisationId(ctx.organisationId())
                            // V47 : auto-affectation = brouillon, publie via action explicite
                            .publie(false)
                            .build();

                    tousLesCreneaux.add(nouveau);
                    nouveauxCreneaux.add(nouveau);
                    creneauxParJour.computeIfAbsent(nouveau.getJour(), k -> new ArrayList<>()).add(nouveau);
                    totalAffectes++;
                    changed = true;

                    if (pairIdx + 1 < paires.size()) {
                        List<ExigenceJour> restantes = new ArrayList<>(
                                paires.subList(pairIdx + 1, paires.size()));
                        restantes.sort(comparateurMrv(ctx.employes(), tousLesCreneaux, ctx));
                        for (int i = 0; i < restantes.size(); i++) {
                            paires.set(pairIdx + 1 + i, restantes.get(i));
                        }
                    }
                    break;
                }
            }
        }

        // ── 4. Post-processing ───────────────────────────────────────────
        appliquer2Swap(nouveauxCreneaux, tousLesCreneaux, ctx, dateParJour);
        CreneauPostProcessor.fusionnerCreneauxContigus(nouveauxCreneaux);
        CreneauPostProcessor.logSlotsNonCouverts(ctx, creneauxParJour);

        return new SolverResult(totalAffectes, nouveauxCreneaux);
    }

    // =========================================================================
    // MRV pair construction
    // =========================================================================

    private List<ExigenceJour> construirePaires(List<Exigence> exigences, List<Employe> employes) {
        List<ExigenceJour> paires = new ArrayList<>();
        for (Exigence ex : exigences) {
            for (int jour : ex.getJours()) {
                paires.add(new ExigenceJour(ex, jour));
            }
        }
        paires.sort(comparateurMrvInitial(employes));
        return paires;
    }

    private Comparator<ExigenceJour> comparateurMrvInitial(List<Employe> employes) {
        return Comparator.comparingDouble(paire -> {
            long candidats = employes.stream()
                    .filter(emp -> emp.hasRole(paire.exigence().getRole()))
                    .count();
            int requis = paire.exigence().getNombreRequis();
            return requis == 0 ? Double.MAX_VALUE : (double) candidats / requis;
        });
    }

    private Comparator<ExigenceJour> comparateurMrv(List<Employe> employes,
                                                      List<CreneauAssigne> creneaux,
                                                      ContexteAffectation ctx) {
        Map<Integer, LocalDate> dpj = construireDateParJour(ctx.lundi());
        return Comparator.comparingDouble(paire -> {
            LocalDate dateJour = dpj.get(paire.jour());
            long candidats = employes.stream()
                    .filter(emp -> emp.hasRole(paire.exigence().getRole()))
                    .filter(emp -> emp.getSiteIds() != null
                            && emp.getSiteIds().contains(paire.exigence().getSiteId()))
                    .filter(emp -> aDisponibiliteJour(emp, paire.jour()))
                    .filter(emp -> dateJour == null || !estEnConge(ctx.congesApprouves(),
                            emp.getId(), dateJour,
                            paire.exigence().getHeureDebut(), paire.exigence().getHeureFin()))
                    .filter(emp -> getHeuresSemaine(creneaux, emp.getId(), ctx.semaine())
                            < ctx.heuresMaxSemaine() - ctx.dureeMin() + EPSILON)
                    .count();
            int requis = paire.exigence().getNombreRequis();
            return requis == 0 ? Double.MAX_VALUE : (double) candidats / requis;
        });
    }

    // =========================================================================
    // Coverage analysis
    // =========================================================================

    private List<Double> trouverHeuresManquantes(Exigence exigence, int jour,
                                                   Map<Integer, List<CreneauAssigne>> creneauxParJour,
                                                   ContexteAffectation ctx) {
        List<Double> manquantes = new ArrayList<>();
        List<CreneauAssigne> creneauxJour = creneauxParJour.getOrDefault(jour, List.of());

        // Pause fixe collective: determine if the fixed break window applies to this day
        boolean pauseFixeActive = ctx.pauseFixeHeureDebut() != null
                && ctx.pauseFixeHeureFin() != null
                && ctx.pauseFixeHeureDebut() > EPSILON
                && ctx.pauseFixeJours() != null
                && ctx.pauseFixeJours().contains(jour);

        int steps = (int) Math.round(
                (exigence.getHeureFin() - exigence.getHeureDebut()) / ctx.granularite());
        for (int i = 0; i < steps; i++) {
            final double slot = exigence.getHeureDebut() + i * ctx.granularite();

            // Layer 1: skip slots inside the fixed collective break window
            // Uses symmetric comparison: slot in [debut, fin[ (half-open interval)
            if (pauseFixeActive
                    && slot >= ctx.pauseFixeHeureDebut() - EPSILON
                    && slot < ctx.pauseFixeHeureFin()) {
                continue; // this slot is during the collective break — not "missing"
            }

            long countRole = creneauxJour.stream()
                    .filter(c -> c.getSiteId().equals(exigence.getSiteId()))
                    .filter(c -> slot >= c.getHeureDebut() - EPSILON
                              && slot < c.getHeureFin() - EPSILON)
                    // Sprint 16 : match the creneau's captured role first (if populated),
                    // else fall back to any role the employee holds.
                    .filter(c -> {
                        if (c.getRole() != null) {
                            return c.getRole().equals(exigence.getRole());
                        }
                        Employe emp = ctx.employeParId().get(c.getEmployeId());
                        return emp != null && emp.hasRole(exigence.getRole());
                    })
                    .count();

            int totalRequired = getTotalRequiredForSlot(
                    ctx.exigences(), exigence.getSiteId(), exigence.getRole(), jour, slot);
            if (countRole < totalRequired) {
                manquantes.add(slot);
            }
        }
        return manquantes;
    }

    private Set<String> trouverDejaPresentsComplets(Exigence exigence, int jour,
                                                      List<Double> heuresManquantes,
                                                      Map<Integer, List<CreneauAssigne>> creneauxParJour,
                                                      ContexteAffectation ctx) {
        List<CreneauAssigne> creneauxJour = creneauxParJour.getOrDefault(jour, List.of());

        Set<String> candidatsAssignes = new HashSet<>();
        for (CreneauAssigne c : creneauxJour) {
            if (!c.getSiteId().equals(exigence.getSiteId())) continue;
            // Sprint 16 : prefer the creneau's captured role (authoritative if set),
            // else fall back to whether the employee holds the exigence role.
            boolean matches;
            if (c.getRole() != null) {
                matches = c.getRole().equals(exigence.getRole());
            } else {
                Employe emp = ctx.employeParId().get(c.getEmployeId());
                matches = emp != null && emp.hasRole(exigence.getRole());
            }
            if (matches) {
                candidatsAssignes.add(c.getEmployeId());
            }
        }

        Set<String> couvrantTout = new HashSet<>();
        for (String empId : candidatsAssignes) {
            boolean couvreAll = true;
            for (double slot : heuresManquantes) {
                boolean coveredByEmp = creneauxJour.stream()
                        .filter(c -> c.getEmployeId().equals(empId))
                        .filter(c -> c.getSiteId().equals(exigence.getSiteId()))
                        .anyMatch(c -> slot >= c.getHeureDebut() - EPSILON
                                    && slot < c.getHeureFin() - EPSILON);
                if (!coveredByEmp) { couvreAll = false; break; }
            }
            if (couvreAll) couvrantTout.add(empId);
        }
        return couvrantTout;
    }

    // =========================================================================
    // Candidate filtering & sorting
    // =========================================================================

    private List<Employe> filtrerCandidats(Exigence exigence, int jour,
                                            Set<String> dejaPresents,
                                            ContexteAffectation ctx) {
        return ctx.employes().stream()
                .filter(emp -> emp.hasRole(exigence.getRole()))
                .filter(emp -> emp.getSiteIds() != null
                        && emp.getSiteIds().contains(exigence.getSiteId()))
                .filter(emp -> !dejaPresents.contains(emp.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void trierCandidats(List<Employe> candidats, List<String> regles,
                                 int jour, List<CreneauAssigne> creneaux,
                                 ContexteAffectation ctx) {
        if (regles.contains("anciennete")) {
            for (Employe emp : candidats) {
                if (emp.getDateEmbauche() == null) {
                    log.warn("[Affectation] Employe {} ({}) sans dateEmbauche — traite comme le plus junior",
                            emp.getId(), emp.getNom());
                }
            }
        }

        candidats.sort((a, b) -> {
            for (String regle : regles) {
                int diff = 0;
                switch (regle) {
                    case "disponibilite" -> diff = Double.compare(getDispoJour(b, jour), getDispoJour(a, jour));
                    case "equite" -> diff = Double.compare(
                            getHeuresSemaine(creneaux, a.getId(), ctx.semaine()),
                            getHeuresSemaine(creneaux, b.getId(), ctx.semaine()));
                    case "anciennete" -> {
                        LocalDate dA = a.getDateEmbauche() != null ? a.getDateEmbauche() : LocalDate.MAX;
                        LocalDate dB = b.getDateEmbauche() != null ? b.getDateEmbauche() : LocalDate.MAX;
                        diff = dA.compareTo(dB);
                    }
                    case "age" -> {
                        LocalDate dA = a.getDateNaissance() != null ? a.getDateNaissance() : LocalDate.MAX;
                        LocalDate dB = b.getDateNaissance() != null ? b.getDateNaissance() : LocalDate.MAX;
                        diff = dA.compareTo(dB);
                    }
                    default -> { /* unknown rule */ }
                }
                if (diff != 0) return diff;
            }
            return a.getId().compareTo(b.getId());
        });
    }

    // =========================================================================
    // 2-swap post-processing
    // =========================================================================

    private void appliquer2Swap(List<CreneauAssigne> nouveauxCreneaux,
                                  List<CreneauAssigne> tousLesCreneaux,
                                  ContexteAffectation ctx,
                                  Map<Integer, LocalDate> dateParJour) {
        // Fix B4: allow 2-swap when equity is the PRIMARY rule (first in list),
        // not only when it's the sole rule. This enables equity optimization for
        // typical configs like ["equite", "anciennete"] where equity is the main
        // objective and anciennete is just a tiebreaker.
        if (ctx.regles().isEmpty() || !ctx.regles().get(0).equals("equite")) return;

        final int MAX_SWAP_ITERATIONS = 100;

        for (int iter = 0; iter < MAX_SWAP_ITERATIONS; iter++) {
            boolean improved = false;

            // Sprint 16 / Feature 2 : a multi-role employee is placed in EVERY group
            // they belong to, so the 2-swap optimizer can consider them as a
            // candidate for any of their roles' equity rebalancing.
            Map<String, List<Employe>> parRole = new TreeMap<>();
            for (Employe emp : ctx.employes()) {
                if (emp.getRoles() == null || emp.getRoles().isEmpty()) {
                    parRole.computeIfAbsent("", k -> new ArrayList<>()).add(emp);
                } else {
                    for (String role : emp.getRoles()) {
                        parRole.computeIfAbsent(role, k -> new ArrayList<>()).add(emp);
                    }
                }
            }

            for (List<Employe> groupe : parRole.values()) {
                if (groupe.size() < 2) continue;

                Employe empMax = groupe.stream()
                        .max(Comparator.comparingDouble(e -> getHeuresSemaine(tousLesCreneaux, e.getId(), ctx.semaine())))
                        .orElse(null);
                Employe empMin = groupe.stream()
                        .min(Comparator.comparingDouble(e -> getHeuresSemaine(tousLesCreneaux, e.getId(), ctx.semaine())))
                        .orElse(null);

                if (empMax == null || empMin == null || empMax.getId().equals(empMin.getId())) continue;

                double heuresMax = getHeuresSemaine(tousLesCreneaux, empMax.getId(), ctx.semaine());
                double heuresMin = getHeuresSemaine(tousLesCreneaux, empMin.getId(), ctx.semaine());
                if (heuresMax - heuresMin < ctx.granularite() + EPSILON) continue;

                for (CreneauAssigne creneau : nouveauxCreneaux) {
                    if (!creneau.getEmployeId().equals(empMax.getId())) continue;

                    int jour = creneau.getJour();
                    LocalDate dateJour = dateParJour.get(jour);
                    if (dateJour == null) continue;

                    if (empMin.getSiteIds() == null || !empMin.getSiteIds().contains(creneau.getSiteId())) continue;
                    if (!estDisponiblePlage(empMin, jour, creneau.getHeureDebut(), creneau.getHeureFin(), ctx.granularite())) continue;
                    if (estEnConge(ctx.congesApprouves(), empMin.getId(), dateJour, creneau.getHeureDebut(), creneau.getHeureFin())) continue;
                    if (aConflitCrossSite(empMin.getId(), jour, creneau.getHeureDebut(), creneau.getHeureFin(), tousLesCreneaux, ctx.semaine())) continue;

                    double heuresMinActuelles = getHeuresSemaine(tousLesCreneaux, empMin.getId(), ctx.semaine());
                    double dureeCreneau = creneau.getHeureFin() - creneau.getHeureDebut();
                    if (heuresMinActuelles + dureeCreneau > ctx.heuresMaxSemaine() + EPSILON) continue;

                    // Fix B1: check dureeMaxJour for empMin on the swap target day
                    double heuresJourMin = getHeuresJour(tousLesCreneaux, empMin.getId(), jour, ctx.semaine());
                    if (heuresJourMin + dureeCreneau > ctx.dureeMaxJour() + EPSILON) continue;

                    // L1/L2/L5 checks for the swap target
                    if (aViolationReposEntreShifts(empMin.getId(), jour, creneau.getHeureDebut(),
                            creneau.getHeureFin(), tousLesCreneaux, ctx.semaine(), ctx.reposMinEntreShifts())) continue;
                    if (aViolationReposHebdo(empMin.getId(), jour, tousLesCreneaux, ctx.semaine(), ctx.reposHebdoMin())) continue;
                    if (depasseMaxJoursConsecutifs(empMin.getId(), jour, tousLesCreneaux, ctx.semaine(), ctx.maxJoursConsecutifs())) continue;

                    creneau.setEmployeId(empMin.getId());
                    improved = true;
                    break;
                }
                if (improved) break;
            }
            if (!improved) break;
        }
    }

    // =========================================================================
    // Internal record
    // =========================================================================

    record ExigenceJour(Exigence exigence, int jour) {}
}

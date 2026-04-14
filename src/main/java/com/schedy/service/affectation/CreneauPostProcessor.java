package com.schedy.service.affectation;

import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.Exigence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.schedy.service.affectation.SchedulingConstraints.EPSILON;

/**
 * Post-processing operations on newly created creneaux: merging adjacent blocks
 * and logging uncovered slots. Extracted from GreedySolver for single-responsibility.
 */
public final class CreneauPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(CreneauPostProcessor.class);

    private CreneauPostProcessor() {}

    /**
     * Merges adjacent creneaux for the same employee, day, and site into a single
     * creneau. Uses a single-pass running-block algorithm that correctly handles
     * chains of 3+ contiguous blocks.
     */
    public static void fusionnerCreneauxContigus(List<CreneauAssigne> creneaux) {
        if (creneaux.size() < 2) return;

        creneaux.sort(Comparator.comparing(CreneauAssigne::getEmployeId)
                .thenComparing(CreneauAssigne::getJour)
                .thenComparing(CreneauAssigne::getSiteId)
                .thenComparingDouble(CreneauAssigne::getHeureDebut));

        List<CreneauAssigne> toRemove = new ArrayList<>();
        CreneauAssigne current = creneaux.get(0);
        for (int i = 1; i < creneaux.size(); i++) {
            CreneauAssigne next = creneaux.get(i);
            if (current.getEmployeId().equals(next.getEmployeId())
                    && current.getJour() == next.getJour()
                    && current.getSiteId().equals(next.getSiteId())
                    && Math.abs(current.getHeureFin() - next.getHeureDebut()) < EPSILON) {
                current.setHeureFin(next.getHeureFin());
                toRemove.add(next);
            } else {
                current = next;
            }
        }
        creneaux.removeAll(toRemove);
    }

    /**
     * Logs uncovered slots after the greedy phase completes, providing visibility
     * to admins about exigences that could not be fully satisfied.
     */
    public static void logSlotsNonCouverts(ContexteAffectation ctx,
                                             Map<Integer, List<CreneauAssigne>> creneauxParJour) {
        for (Exigence ex : ctx.exigences()) {
            for (int jour : ex.getJours()) {
                List<Double> manquantes = trouverHeuresManquantesForLog(ex, jour, creneauxParJour, ctx);
                if (!manquantes.isEmpty()) {
                    log.info("[Affectation] Exigence '{}' jour {} : {}/{} slots non couverts (heures: {})",
                            ex.getLibelle(), jour, manquantes.size(),
                            (int) Math.round((ex.getHeureFin() - ex.getHeureDebut()) / ctx.granularite()),
                            manquantes.stream().map(h -> String.format("%.1f", h))
                                    .reduce((a, b) -> a + "," + b).orElse(""));
                }
            }
        }
    }

    /**
     * Simplified version of trouverHeuresManquantes used only for logging.
     * Uses the same aggregate requirement logic.
     */
    private static List<Double> trouverHeuresManquantesForLog(
            Exigence exigence, int jour,
            Map<Integer, List<CreneauAssigne>> creneauxParJour,
            ContexteAffectation ctx) {
        List<Double> manquantes = new ArrayList<>();
        List<CreneauAssigne> creneauxJour = creneauxParJour.getOrDefault(jour, List.of());

        int steps = (int) Math.round(
                (exigence.getHeureFin() - exigence.getHeureDebut()) / ctx.granularite());
        for (int i = 0; i < steps; i++) {
            double slot = exigence.getHeureDebut() + i * ctx.granularite();
            long countRole = creneauxJour.stream()
                    .filter(c -> c.getSiteId().equals(exigence.getSiteId()))
                    .filter(c -> slot >= c.getHeureDebut() - EPSILON
                              && slot < c.getHeureFin() - EPSILON)
                    // Sprint 16 : prefer the creneau's captured role (V33), else
                    // fall back to any role the employee holds (multi-role).
                    .filter(c -> {
                        if (c.getRole() != null) {
                            return c.getRole().equals(exigence.getRole());
                        }
                        var emp = ctx.employeParId().get(c.getEmployeId());
                        return emp != null && emp.hasRole(exigence.getRole());
                    })
                    .count();

            int totalRequired = SchedulingConstraints.getTotalRequiredForSlot(
                    ctx.exigences(), exigence.getSiteId(), exigence.getRole(), jour, slot);
            if (countRole < totalRequired) {
                manquantes.add(slot);
            }
        }
        return manquantes;
    }
}

package com.schedy.service.affectation;

import com.schedy.entity.Parametres;
import com.schedy.entity.ReglePause;
import com.schedy.entity.TypePause;

import java.util.List;

/**
 * Pure stateless calculator for break deductions and net hours.
 * Handles both simple mode (single threshold) and advanced mode (tiered rules).
 */
public final class BreakCalculator {

    private BreakCalculator() {}

    /**
     * Computes the total break minutes to deduct for a shift of the given duration,
     * based on the organization's pause rules.
     *
     * @param shiftDurationHeures actual shift duration in hours
     * @param parametres          the site/org parameters
     * @return a {@link BreakDeduction} with total unpaid/paid break minutes
     */
    public static BreakDeduction computeDeduction(double shiftDurationHeures, Parametres parametres) {
        if (Boolean.TRUE.equals(parametres.getPauseAvancee()) && parametres.getReglesPause() != null
                && !parametres.getReglesPause().isEmpty()) {
            return computeAdvanced(shiftDurationHeures, parametres.getReglesPause());
        }
        return computeSimple(shiftDurationHeures, parametres);
    }

    /**
     * Simple mode: single threshold + single break.
     */
    private static BreakDeduction computeSimple(double shiftDurationHeures, Parametres parametres) {
        double seuil = parametres.getPauseSeuilHeures() != null ? parametres.getPauseSeuilHeures() : 0;
        int duree = parametres.getPauseDureeMinutes() != null ? parametres.getPauseDureeMinutes() : 0;
        boolean payee = Boolean.TRUE.equals(parametres.getPausePayee());

        if (seuil <= 0 || duree <= 0 || shiftDurationHeures < seuil) {
            return BreakDeduction.NONE;
        }

        return new BreakDeduction(
                payee ? 0 : duree,  // unpaid minutes
                payee ? duree : 0,  // paid minutes
                duree               // total break minutes
        );
    }

    /**
     * Advanced mode: find the applicable tier (self-contained) and sum breaks.
     * Each tier is self-contained — no inheritance from lower tiers.
     */
    private static BreakDeduction computeAdvanced(double shiftDurationHeures, List<ReglePause> regles) {
        // Find all rules in the highest applicable tier
        // A tier is defined by its seuilMinHeures. All rules sharing the same seuil form a tier.
        double bestSeuil = -1;
        for (ReglePause r : regles) {
            Double max = r.getSeuilMaxHeures();
            if (shiftDurationHeures >= r.getSeuilMinHeures()
                    && (max == null || shiftDurationHeures < max)
                    && r.getSeuilMinHeures() > bestSeuil) {
                bestSeuil = r.getSeuilMinHeures();
            }
        }

        if (bestSeuil < 0) return BreakDeduction.NONE;

        int unpaidMinutes = 0;
        int paidMinutes = 0;
        int totalMinutes = 0;

        final double tier = bestSeuil;
        for (ReglePause r : regles) {
            Double max = r.getSeuilMaxHeures();
            boolean inTier = Math.abs(r.getSeuilMinHeures() - tier) < 0.001
                    && (max == null || shiftDurationHeures < max);
            if (!inTier) continue;

            totalMinutes += r.getDureeMinutes();
            if (r.isPayee()) {
                paidMinutes += r.getDureeMinutes();
            } else {
                unpaidMinutes += r.getDureeMinutes();
            }
        }

        return new BreakDeduction(unpaidMinutes, paidMinutes, totalMinutes);
    }

    /**
     * Result of break deduction calculation.
     *
     * @param unpaidMinutes minutes of unpaid break (deducted from payable hours)
     * @param paidMinutes   minutes of paid break (included in payable hours)
     * @param totalMinutes  total break time (unpaid + paid)
     */
    public record BreakDeduction(int unpaidMinutes, int paidMinutes, int totalMinutes) {
        public static final BreakDeduction NONE = new BreakDeduction(0, 0, 0);

        /** Net payable hours = gross - unpaid breaks. */
        public double netPayableHours(double grossHours) {
            return grossHours - (unpaidMinutes / 60.0);
        }
    }
}

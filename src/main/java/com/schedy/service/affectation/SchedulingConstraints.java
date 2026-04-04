package com.schedy.service.affectation;

import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.Employe;
import com.schedy.entity.Exigence;
import com.schedy.entity.JourFerie;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure stateless utility class containing all constraint checks, metrics, and
 * helper functions used by the scheduling algorithm. Extracted from GreedySolver
 * for reuse (e.g. by ReplacementService) and to keep the solver focused on
 * orchestration logic.
 *
 * <p>All methods are static. No Spring or repository dependencies.
 */
public final class SchedulingConstraints {

    /** Floating-point epsilon used for granularity-aware double comparisons. */
    public static final double EPSILON = 1e-9;

    private SchedulingConstraints() {} // utility class

    // =========================================================================
    // Constraint checkers
    // =========================================================================

    /**
     * Returns true if the given date is a public holiday for the given site.
     * Holidays with {@code siteId == null} apply to ALL sites (org-wide).
     * Supports recurrent holidays (same month/day regardless of year).
     */
    public static boolean estJourFerie(List<JourFerie> feries, LocalDate date, String siteId) {
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
    public static boolean estEnConge(List<DemandeConge> conges, String employeId,
                                      LocalDate date, double slotDebut, double slotFin) {
        return conges.stream().anyMatch(d -> {
            if (!d.getEmployeId().equals(employeId)) return false;
            if (date.isBefore(d.getDateDebut()) || date.isAfter(d.getDateFin())) return false;

            if (d.getHeureDebut() == null || d.getHeureFin() == null) return true;

            double leaveStart = date.equals(d.getDateDebut()) ? d.getHeureDebut() : 0.0;
            double leaveEnd   = date.equals(d.getDateFin())   ? d.getHeureFin()   : 24.0;

            return slotDebut < leaveEnd - EPSILON && leaveStart < slotFin - EPSILON;
        });
    }

    /**
     * Returns true if the employee has a disponibilite slot covering the given
     * time slot [temps, temps + granularite[.
     */
    public static boolean estDisponible(Employe emp, int jour, double temps, double granularite) {
        return emp.getDisponibilites().stream().anyMatch(d ->
                d.getJour() == jour
                        && temps >= d.getHeureDebut() - EPSILON
                        && temps + granularite <= d.getHeureFin() + EPSILON);
    }

    /**
     * Returns true if the employee is available for the entire contiguous block
     * [heureDebut, heureFin[. Uses counter-based loop to avoid float drift.
     */
    public static boolean estDisponiblePlage(Employe emp, int jour,
                                              double heureDebut, double heureFin,
                                              double granularite) {
        int steps = (int) Math.round((heureFin - heureDebut) / granularite);
        for (int i = 0; i < steps; i++) {
            double h = heureDebut + i * granularite;
            if (!estDisponible(emp, jour, h, granularite)) return false;
        }
        return true;
    }

    /**
     * Returns true if the employee is already assigned on ANY site during
     * [heureDebut, heureFin[ on the given day and week (cross-site conflict).
     */
    public static boolean aConflitCrossSite(String employeId, int jour,
                                             double heureDebut, double heureFin,
                                             List<CreneauAssigne> tousLesCreneaux,
                                             String semaine) {
        return tousLesCreneaux.stream()
                .filter(c -> c.getEmployeId().equals(employeId))
                .filter(c -> c.getSemaine().equals(semaine))
                .filter(c -> c.getJour() == jour)
                .anyMatch(c -> heureDebut < c.getHeureFin() - EPSILON
                            && c.getHeureDebut() < heureFin - EPSILON);
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    /** Counts the employee's total weekly hours across ALL sites. */
    public static double getHeuresSemaine(List<CreneauAssigne> creneaux,
                                           String employeId, String semaine) {
        return creneaux.stream()
                .filter(c -> c.getEmployeId().equals(employeId))
                .filter(c -> c.getSemaine().equals(semaine))
                .mapToDouble(c -> c.getHeureFin() - c.getHeureDebut())
                .sum();
    }

    /** Returns the employee's total hours on a specific day, across all sites. */
    public static double getHeuresJour(List<CreneauAssigne> creneaux, String employeId,
                                        int jour, String semaine) {
        return creneaux.stream()
                .filter(c -> c.getEmployeId().equals(employeId))
                .filter(c -> c.getSemaine().equals(semaine))
                .filter(c -> c.getJour() == jour)
                .mapToDouble(c -> c.getHeureFin() - c.getHeureDebut())
                .sum();
    }

    /** Returns the total disponibilite duration for the employee on the given day. */
    public static double getDispoJour(Employe emp, int jour) {
        return emp.getDisponibilites().stream()
                .filter(d -> d.getJour() == jour)
                .mapToDouble(d -> d.getHeureFin() - d.getHeureDebut())
                .sum();
    }

    /** Returns true if the employee has any declared disponibilite on the given day. */
    public static boolean aDisponibiliteJour(Employe emp, int jour) {
        return emp.getDisponibilites().stream()
                .anyMatch(d -> d.getJour() == jour);
    }

    /**
     * Computes the total number of employees required at a given slot across
     * ALL exigences that overlap it (same site, role, day, hour range).
     * Ensures additive exigences are correctly handled.
     */
    public static int getTotalRequiredForSlot(List<Exigence> allExigences, String siteId,
                                               String role, int jour, double slot) {
        return allExigences.stream()
                .filter(ex -> ex.getSiteId().equals(siteId))
                .filter(ex -> Objects.equals(ex.getRole(), role))
                .filter(ex -> ex.getJours().contains(jour))
                .filter(ex -> slot >= ex.getHeureDebut() - EPSILON
                           && slot < ex.getHeureFin() - EPSILON)
                .mapToInt(Exigence::getNombreRequis)
                .sum();
    }

    // =========================================================================
    // Grouping & indexing utilities
    // =========================================================================

    /**
     * Groups a sorted list of slot start times into contiguous [debut, fin] blocks.
     * Uses granularite as the step size and epsilon for double comparison.
     */
    public static List<double[]> grouperConsecutives(List<Double> heures, double granularite) {
        if (heures.isEmpty()) return List.of();

        List<double[]> plages = new ArrayList<>();
        double debut = heures.get(0);
        double fin   = heures.get(0) + granularite;

        for (int i = 1; i < heures.size(); i++) {
            double prochainAttendu = fin;
            double prochainReel    = heures.get(i);
            if (Math.abs(prochainReel - prochainAttendu) < EPSILON) {
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

    /** Builds a Map&lt;jour, List&lt;CreneauAssigne&gt;&gt; index for creneaux of the given week. */
    public static Map<Integer, List<CreneauAssigne>> indexerParJour(List<CreneauAssigne> creneaux,
                                                                     String semaine) {
        Map<Integer, List<CreneauAssigne>> index = new HashMap<>();
        for (CreneauAssigne c : creneaux) {
            if (c.getSemaine().equals(semaine)) {
                index.computeIfAbsent(c.getJour(), k -> new ArrayList<>()).add(c);
            }
        }
        return index;
    }

    /** Pre-computes jour (0 = Monday … 6 = Sunday) → LocalDate for the week. */
    public static Map<Integer, LocalDate> construireDateParJour(LocalDate lundi) {
        Map<Integer, LocalDate> map = new HashMap<>();
        for (int jour = 0; jour <= 6; jour++) {
            map.put(jour, lundi.plusDays(jour));
        }
        return map;
    }

    // =========================================================================
    // Labor law constraints (country-agnostic, parameter-driven)
    // =========================================================================

    /**
     * L1 — Returns true if assigning the employee to [heureDebut, heureFin[ on the
     * given day would violate the minimum rest period between shifts.
     *
     * <p>Checks the gap between the proposed shift and the employee's closest
     * existing shift on adjacent days (previous day's last shift, next day's first shift).
     *
     * @param reposMin minimum rest in hours (e.g. 11.0 for France). 0 = disabled.
     */
    public static boolean aViolationReposEntreShifts(String employeId, int jour,
                                                       double heureDebut, double heureFin,
                                                       List<CreneauAssigne> tousLesCreneaux,
                                                       String semaine, double reposMin) {
        if (reposMin <= EPSILON) return false; // disabled

        for (CreneauAssigne c : tousLesCreneaux) {
            if (!c.getEmployeId().equals(employeId)) continue;
            if (!c.getSemaine().equals(semaine)) continue;

            // Same day: no rest check needed (handled by cross-site conflict)
            if (c.getJour() == jour) continue;

            // Modular distance for wrap-around (Sun→Mon = adjacent, not 6 apart)
            int dayDiff = Math.min(
                    Math.abs(c.getJour() - jour),
                    7 - Math.abs(c.getJour() - jour));
            if (dayDiff > 1) continue; // only adjacent days matter

            double gap;
            if (c.getJour() == (jour - 1 + 7) % 7) {
                // Previous day: rest = (proposed start) + 24 - (previous end)
                gap = heureDebut + 24.0 - c.getHeureFin();
            } else {
                // Next day: rest = (next start) + 24 - (proposed end)
                gap = c.getHeureDebut() + 24.0 - heureFin;
            }

            if (gap < reposMin - EPSILON) return true; // violation
        }
        return false;
    }

    /**
     * L2 — Returns true if assigning the employee on the given day would violate
     * the minimum weekly rest requirement.
     *
     * <p>Weekly rest requires a consecutive block of at least {@code reposHebdoMin}
     * hours free within the 7-day week. This checks if any such block still exists
     * if the employee works on the proposed day.
     *
     * @param reposHebdoMin minimum weekly rest in hours (e.g. 35.0 for France). 0 = disabled.
     */
    public static boolean aViolationReposHebdo(String employeId, int jourPropose,
                                                 List<CreneauAssigne> tousLesCreneaux,
                                                 String semaine, double reposHebdoMin) {
        if (reposHebdoMin <= EPSILON) return false; // disabled

        // Build a set of days the employee works (including the proposed day)
        boolean[] travaille = new boolean[7];
        for (CreneauAssigne c : tousLesCreneaux) {
            if (c.getEmployeId().equals(employeId) && c.getSemaine().equals(semaine)) {
                travaille[c.getJour()] = true;
            }
        }
        travaille[jourPropose] = true;

        // Find the longest consecutive rest period (in full days * 24h)
        // A rest day = a day with no assignment
        int maxConsecutiveRestDays = 0;
        int currentRest = 0;
        for (int j = 0; j < 7; j++) {
            if (!travaille[j]) {
                currentRest++;
                maxConsecutiveRestDays = Math.max(maxConsecutiveRestDays, currentRest);
            } else {
                currentRest = 0;
            }
        }
        // Also check wrap-around (end of week → start of week)
        if (!travaille[6] && !travaille[0]) {
            int wrapRest = 0;
            for (int j = 6; j >= 0 && !travaille[j]; j--) wrapRest++;
            for (int j = 0; j < 7 && !travaille[j]; j++) wrapRest++;
            maxConsecutiveRestDays = Math.max(maxConsecutiveRestDays, wrapRest);
        }

        double maxRestHours = maxConsecutiveRestDays * 24.0;
        return maxRestHours < reposHebdoMin - EPSILON; // violation if no sufficient rest block
    }

    /**
     * L5 — Returns true if assigning the employee on the given day would exceed
     * the maximum consecutive working days.
     *
     * @param maxJours max consecutive days (e.g. 6 for France). 0 = disabled.
     */
    public static boolean depasseMaxJoursConsecutifs(String employeId, int jourPropose,
                                                       List<CreneauAssigne> tousLesCreneaux,
                                                       String semaine, int maxJours) {
        if (maxJours <= 0) return false; // disabled

        boolean[] travaille = new boolean[7];
        for (CreneauAssigne c : tousLesCreneaux) {
            if (c.getEmployeId().equals(employeId) && c.getSemaine().equals(semaine)) {
                travaille[c.getJour()] = true;
            }
        }
        travaille[jourPropose] = true;

        // Count max consecutive working days
        int maxConsecutive = 0;
        int current = 0;
        for (int j = 0; j < 7; j++) {
            if (travaille[j]) {
                current++;
                maxConsecutive = Math.max(maxConsecutive, current);
            } else {
                current = 0;
            }
        }
        // Wrap-around check
        if (travaille[6] && travaille[0]) {
            int wrapWork = 0;
            for (int j = 6; j >= 0 && travaille[j]; j--) wrapWork++;
            for (int j = 0; j < 7 && travaille[j]; j++) wrapWork++;
            maxConsecutive = Math.max(maxConsecutive, wrapWork);
        }

        return maxConsecutive > maxJours;
    }
}

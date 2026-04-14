package com.schedy.service.affectation;

import com.schedy.entity.CreneauAssigne;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.DisponibilitePlage;
import com.schedy.entity.Employe;
import com.schedy.entity.Exigence;
import com.schedy.entity.JourFerie;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared test fixtures and builder helpers for GreedySolver test suites.
 *
 * <p>Reference week: 2025-W02, Monday = 2025-01-06 (jour index 0).
 */
public final class SolverTestFixtures {

    public static final String SEMAINE   = "2025-W02";
    public static final LocalDate LUNDI  = LocalDate.of(2025, 1, 6);
    public static final String SITE_A    = "site-A";
    public static final String SITE_B    = "site-B";
    public static final String ORG       = "org1";

    private SolverTestFixtures() {}

    /** Builds an employee available from heureDebut to heureFin on the given jour index. */
    public static Employe buildEmploye(String id, String nom, String role,
                                        String siteId,
                                        int jour, double heureDebut, double heureFin) {
        return Employe.builder()
                .id(id).nom(nom).roles(List.of(role))
                .siteIds(List.of(siteId))
                .disponibilites(List.of(
                        DisponibilitePlage.builder()
                                .jour(jour).heureDebut(heureDebut).heureFin(heureFin).build()))
                .build();
    }

    /** Builds an employee available on multiple days/slots. */
    public static Employe buildEmployeMultiDispo(String id, String nom, String role,
                                                  String siteId,
                                                  List<DisponibilitePlage> dispos) {
        return Employe.builder()
                .id(id).nom(nom).roles(List.of(role))
                .siteIds(List.of(siteId))
                .disponibilites(dispos)
                .build();
    }

    /** Builds an employee belonging to multiple sites. */
    public static Employe buildEmployeMultiSite(String id, String nom, String role,
                                                 List<String> siteIds,
                                                 int jour, double heureDebut, double heureFin) {
        return Employe.builder()
                .id(id).nom(nom).roles(List.of(role))
                .siteIds(siteIds)
                .disponibilites(List.of(
                        DisponibilitePlage.builder()
                                .jour(jour).heureDebut(heureDebut).heureFin(heureFin).build()))
                .build();
    }

    /** Builds an exigence requiring {@code nombreRequis} employees of the given role. */
    public static Exigence buildExigence(String id, String libelle, String role,
                                          String siteId, List<Integer> jours,
                                          double heureDebut, double heureFin,
                                          int nombreRequis) {
        return Exigence.builder()
                .id(id).libelle(libelle).role(role)
                .siteId(siteId).jours(jours)
                .heureDebut(heureDebut).heureFin(heureFin)
                .nombreRequis(nombreRequis).build();
    }

    /** Builds an existing creneau for the reference week. */
    public static CreneauAssigne buildCreneau(String id, String employeId, String siteId,
                                               int jour, double heureDebut, double heureFin) {
        return CreneauAssigne.builder()
                .id(id).employeId(employeId).siteId(siteId)
                .jour(jour).heureDebut(heureDebut).heureFin(heureFin)
                .semaine(SEMAINE).organisationId(ORG).build();
    }

    /** Builds ContexteAffectation with sensible defaults (no labor constraints). */
    public static ContexteAffectation buildContexte(List<Exigence> exigences,
                                                     List<Employe> employes,
                                                     List<CreneauAssigne> creneauxExistants,
                                                     List<DemandeConge> conges,
                                                     List<JourFerie> feries,
                                                     double dureeMin,
                                                     double granularite,
                                                     List<String> regles,
                                                     double heuresMax) {
        return buildContexte(exigences, employes, creneauxExistants, conges, feries,
                dureeMin, granularite, regles, heuresMax, 10.0, 0.0, 0.0, 0);
    }

    /** Builds ContexteAffectation with explicit dureeMaxJour (no labor constraints). */
    public static ContexteAffectation buildContexte(List<Exigence> exigences,
                                                     List<Employe> employes,
                                                     List<CreneauAssigne> creneauxExistants,
                                                     List<DemandeConge> conges,
                                                     List<JourFerie> feries,
                                                     double dureeMin,
                                                     double granularite,
                                                     List<String> regles,
                                                     double heuresMax,
                                                     double dureeMaxJour) {
        return buildContexte(exigences, employes, creneauxExistants, conges, feries,
                dureeMin, granularite, regles, heuresMax, dureeMaxJour, 0.0, 0.0, 0);
    }

    /** Builds ContexteAffectation with ALL parameters including labor law constraints. */
    public static ContexteAffectation buildContexte(List<Exigence> exigences,
                                                     List<Employe> employes,
                                                     List<CreneauAssigne> creneauxExistants,
                                                     List<DemandeConge> conges,
                                                     List<JourFerie> feries,
                                                     double dureeMin,
                                                     double granularite,
                                                     List<String> regles,
                                                     double heuresMax,
                                                     double dureeMaxJour,
                                                     double reposMinEntreShifts,
                                                     double reposHebdoMin,
                                                     int maxJoursConsecutifs) {
        Map<String, Employe> employeParId = employes.stream()
                .collect(Collectors.toMap(Employe::getId, e -> e));
        return new ContexteAffectation(
                exigences, employes, employeParId,
                creneauxExistants, conges, feries,
                dureeMin, granularite, regles, heuresMax, dureeMaxJour,
                reposMinEntreShifts, reposHebdoMin, maxJoursConsecutifs,
                null, null, java.util.List.of(), // pauseFixe disabled by default
                LUNDI, SEMAINE, SITE_A, ORG);
    }

    // =========================================================================
    // Universal invariant assertions — call after every solver run to catch
    // mutations that break constraints without failing individual assertions.
    // =========================================================================

    /**
     * Validates ALL hard constraints on the solver result. Call this in every test
     * to guarantee high mutation-killing coverage.
     */
    public static void assertAllInvariantsHold(SolverResult result,
                                                 ContexteAffectation ctx) {
        assertNoCreneauOutsideDisponibilite(result, ctx);
        assertNoOverlapSameEmployeeDay(result, ctx);
        assertWeeklyCapRespected(result, ctx);
        assertDailyCapRespected(result, ctx);
        assertSiteMembershipRespected(result, ctx);
        assertRoleMatchRespected(result, ctx);
        assertNoCreneauDuringLeave(result, ctx);
        assertReposEntreShiftsRespected(result, ctx);
    }

    /**
     * Invariant 1: no creneau falls outside the employee's declared disponibilites.
     * Supports merged creneaux that span multiple adjacent disponibilite plages
     * (e.g. creneau [9h-11h] covering plages [9h-10h] + [10h-11h]).
     */
    public static void assertNoCreneauOutsideDisponibilite(SolverResult result,
                                                             ContexteAffectation ctx) {
        for (var c : result.nouveauxCreneaux()) {
            var emp = ctx.employeParId().get(c.getEmployeId());
            org.assertj.core.api.Assertions.assertThat(emp)
                    .as("Creneau for unknown employee %s", c.getEmployeId())
                    .isNotNull();
            // Check that EVERY hour in the creneau is covered by at least one disponibilite plage.
            // This handles merged creneaux spanning multiple adjacent plages.
            double gran = ctx.granularite() > 0 ? ctx.granularite() : 1.0;
            int steps = (int) Math.round((c.getHeureFin() - c.getHeureDebut()) / gran);
            for (int i = 0; i < steps; i++) {
                double h = c.getHeureDebut() + i * gran;
                boolean slotCovered = emp.getDisponibilites().stream().anyMatch(d ->
                        d.getJour() == c.getJour()
                                && h >= d.getHeureDebut() - 1e-9
                                && h + gran <= d.getHeureFin() + 1e-9);
                org.assertj.core.api.Assertions.assertThat(slotCovered)
                        .as("Creneau %s jour=%d slot %.1f est HORS disponibilite de %s",
                                c.getEmployeId(), c.getJour(), h, emp.getNom())
                        .isTrue();
            }
        }
    }

    /** Invariant 2: no two creneaux for the same employee on the same day overlap. */
    public static void assertNoOverlapSameEmployeeDay(SolverResult result,
                                                        ContexteAffectation ctx) {
        var allCreneaux = new java.util.ArrayList<>(ctx.creneauxExistants());
        allCreneaux.addAll(result.nouveauxCreneaux());
        var byEmpDay = allCreneaux.stream()
                .collect(Collectors.groupingBy(c -> c.getEmployeId() + "|" + c.getJour()));
        for (var entry : byEmpDay.entrySet()) {
            var list = entry.getValue();
            list.sort(java.util.Comparator.comparingDouble(com.schedy.entity.CreneauAssigne::getHeureDebut));
            for (int i = 0; i < list.size() - 1; i++) {
                var a = list.get(i);
                var b = list.get(i + 1);
                org.assertj.core.api.Assertions.assertThat(a.getHeureFin())
                        .as("Overlap: %s jour=%d [%.1f-%.1f] et [%.1f-%.1f]",
                                a.getEmployeId(), a.getJour(),
                                a.getHeureDebut(), a.getHeureFin(),
                                b.getHeureDebut(), b.getHeureFin())
                        .isLessThanOrEqualTo(b.getHeureDebut() + 1e-9);
            }
        }
    }

    /** Invariant 3: no employee exceeds heuresMaxSemaine. */
    public static void assertWeeklyCapRespected(SolverResult result,
                                                  ContexteAffectation ctx) {
        var allCreneaux = new java.util.ArrayList<>(ctx.creneauxExistants());
        allCreneaux.addAll(result.nouveauxCreneaux());
        var byEmp = allCreneaux.stream()
                .filter(c -> c.getSemaine().equals(ctx.semaine()))
                .collect(Collectors.groupingBy(com.schedy.entity.CreneauAssigne::getEmployeId,
                        Collectors.summingDouble(c -> c.getHeureFin() - c.getHeureDebut())));
        for (var entry : byEmp.entrySet()) {
            org.assertj.core.api.Assertions.assertThat(entry.getValue())
                    .as("Employe %s depasse heuresMaxSemaine (%.1f > %.1f)",
                            entry.getKey(), entry.getValue(), ctx.heuresMaxSemaine())
                    .isLessThanOrEqualTo(ctx.heuresMaxSemaine() + 1e-3);
        }
    }

    /** Invariant 4: no employee exceeds dureeMaxJour on any day. */
    public static void assertDailyCapRespected(SolverResult result,
                                                 ContexteAffectation ctx) {
        var allCreneaux = new java.util.ArrayList<>(ctx.creneauxExistants());
        allCreneaux.addAll(result.nouveauxCreneaux());
        var byEmpDay = allCreneaux.stream()
                .filter(c -> c.getSemaine().equals(ctx.semaine()))
                .collect(Collectors.groupingBy(
                        c -> c.getEmployeId() + "|" + c.getJour(),
                        Collectors.summingDouble(c -> c.getHeureFin() - c.getHeureDebut())));
        for (var entry : byEmpDay.entrySet()) {
            org.assertj.core.api.Assertions.assertThat(entry.getValue())
                    .as("%s depasse dureeMaxJour (%.1f > %.1f)", entry.getKey(), entry.getValue(), ctx.dureeMaxJour())
                    .isLessThanOrEqualTo(ctx.dureeMaxJour() + 1e-3);
        }
    }

    /** Invariant 5: every creneau's site is in the assigned employee's siteIds. */
    public static void assertSiteMembershipRespected(SolverResult result,
                                                       ContexteAffectation ctx) {
        for (var c : result.nouveauxCreneaux()) {
            var emp = ctx.employeParId().get(c.getEmployeId());
            org.assertj.core.api.Assertions.assertThat(emp.getSiteIds())
                    .as("Employe %s n'appartient pas au site %s", c.getEmployeId(), c.getSiteId())
                    .contains(c.getSiteId());
        }
    }

    /** Invariant 6: every creneau's employee holds the exigence's role (multi-role aware, Sprint 16). */
    public static void assertRoleMatchRespected(SolverResult result,
                                                  ContexteAffectation ctx) {
        for (var c : result.nouveauxCreneaux()) {
            var emp = ctx.employeParId().get(c.getEmployeId());
            boolean roleMatch = ctx.exigences().stream().anyMatch(ex ->
                    ex.getSiteId().equals(c.getSiteId())
                            && emp.hasRole(ex.getRole())
                            && ex.getJours().contains(c.getJour()));
            org.assertj.core.api.Assertions.assertThat(roleMatch)
                    .as("Creneau %s (roles=%s) ne correspond a aucune exigence sur site %s jour %d",
                            c.getEmployeId(), emp.getRoles(), c.getSiteId(), c.getJour())
                    .isTrue();
        }
    }

    /** Invariant 7: no creneau overlaps an approved leave for the assigned employee. */
    public static void assertNoCreneauDuringLeave(SolverResult result,
                                                    ContexteAffectation ctx) {
        Map<Integer, java.time.LocalDate> dateParJour =
                com.schedy.service.affectation.SchedulingConstraints.construireDateParJour(ctx.lundi());
        for (var c : result.nouveauxCreneaux()) {
            java.time.LocalDate date = dateParJour.get(c.getJour());
            if (date == null) continue;
            boolean onLeave = com.schedy.service.affectation.SchedulingConstraints.estEnConge(
                    ctx.congesApprouves(), c.getEmployeId(), date,
                    c.getHeureDebut(), c.getHeureFin());
            org.assertj.core.api.Assertions.assertThat(onLeave)
                    .as("Creneau %s jour=%d [%.1f-%.1f] chevauche un conge approuve",
                            c.getEmployeId(), c.getJour(), c.getHeureDebut(), c.getHeureFin())
                    .isFalse();
        }
    }

    /** Invariant 8: inter-shift rest period is respected (when configured). */
    public static void assertReposEntreShiftsRespected(SolverResult result,
                                                         ContexteAffectation ctx) {
        if (ctx.reposMinEntreShifts() <= 1e-9) return; // disabled
        var allCreneaux = new java.util.ArrayList<>(ctx.creneauxExistants());
        allCreneaux.addAll(result.nouveauxCreneaux());
        for (var c : result.nouveauxCreneaux()) {
            boolean violation = com.schedy.service.affectation.SchedulingConstraints
                    .aViolationReposEntreShifts(c.getEmployeId(), c.getJour(),
                            c.getHeureDebut(), c.getHeureFin(),
                            allCreneaux, ctx.semaine(), ctx.reposMinEntreShifts());
            org.assertj.core.api.Assertions.assertThat(violation)
                    .as("Creneau %s jour=%d [%.1f-%.1f] viole le repos inter-shifts (%.0fh min)",
                            c.getEmployeId(), c.getJour(), c.getHeureDebut(), c.getHeureFin(),
                            ctx.reposMinEntreShifts())
                    .isFalse();
        }
    }
}

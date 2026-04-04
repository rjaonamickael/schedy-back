package com.schedy.service.affectation;

import com.schedy.entity.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Immutable input context for the scheduling algorithm.
 * No repository / Spring dependency — pure data.
 */
public record ContexteAffectation(
        List<Exigence> exigences,
        List<Employe> employes,
        Map<String, Employe> employeParId,
        List<CreneauAssigne> creneauxExistants,
        List<DemandeConge> congesApprouves,
        List<JourFerie> joursFeries,
        double dureeMin,
        double granularite,
        List<String> regles,
        double heuresMaxSemaine,
        double dureeMaxJour,
        /** Minimum rest between two shifts in hours. 0 = disabled. */
        double reposMinEntreShifts,
        /** Minimum weekly rest in hours. 0 = disabled. */
        double reposHebdoMin,
        /** Maximum consecutive working days. 0 = disabled. */
        int maxJoursConsecutifs,
        /** Fixed collective break window start hour. Null/0 = disabled. */
        Double pauseFixeHeureDebut,
        /** Fixed collective break window end hour. Null/0 = disabled. */
        Double pauseFixeHeureFin,
        /** Days when the fixed break applies (0=Mon..6=Sun). Empty = disabled. */
        List<Integer> pauseFixeJours,
        LocalDate lundi,
        String semaine,
        String siteId,
        String organisationId
) {}

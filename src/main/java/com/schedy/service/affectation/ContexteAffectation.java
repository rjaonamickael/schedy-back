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
        LocalDate lundi,
        String semaine,
        String siteId,
        String organisationId
) {}

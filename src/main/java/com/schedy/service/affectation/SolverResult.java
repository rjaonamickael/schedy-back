package com.schedy.service.affectation;

import com.schedy.entity.CreneauAssigne;

import java.util.List;

/**
 * Immutable result from an AffectationSolver.
 * Contains only the NEW creneaux to persist (not the existing ones).
 */
public record SolverResult(
        int totalAffectes,
        List<CreneauAssigne> nouveauxCreneaux
) {}

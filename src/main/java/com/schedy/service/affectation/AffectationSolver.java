package com.schedy.service.affectation;

/**
 * Strategy interface for scheduling solvers.
 * Implementations: GreedySolver (current), future OR-Tools CP-SAT, metaheuristics.
 */
public interface AffectationSolver {
    SolverResult resoudre(ContexteAffectation contexte);
}

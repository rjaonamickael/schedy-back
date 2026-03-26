package com.schedy.dto.response;

/**
 * Read-only impact summary returned by GET /api/v1/employes/{id}/impact.
 *
 * The frontend uses this to decide between a simple confirmation dialog
 * and a detailed impact view before a hard delete is executed.
 */
public record EmployeImpactResponse(

        /** Assigned shifts in weeks strictly after the current ISO week */
        long creneauxFuturs,

        /** Assigned shifts in the current ISO week */
        long creneauxSemaineCourante,

        /** Distinct sites this employee appears on */
        long nbSites,

        /** Leave requests with statut = en_attente */
        long demandesCongeEnAttente,

        /** Leave requests with statut = approuve and dateFin >= today */
        long demandesCongeApprouvees,

        /** All time-clock records regardless of statut */
        long nbPointages,

        /** True if the employee has an active Manager login account */
        boolean hasCompteManager
) {}

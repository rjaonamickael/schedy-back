package com.schedy.dto.response;

import com.schedy.entity.Exigence;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 16 / Feature 1 : exposes period-scoping fields (dateDebut, dateFin,
 * priorite) so the frontend can display "applies to Dec 15 - Jan 5" badges
 * on exigences.
 */
public record ExigenceResponse(
        String id,
        String libelle,
        List<Integer> jours,
        double heureDebut,
        double heureFin,
        String role,
        int nombreRequis,
        String siteId,
        LocalDate dateDebut,
        LocalDate dateFin,
        int priorite
) {
    public static ExigenceResponse from(Exigence e) {
        return new ExigenceResponse(
                e.getId(),
                e.getLibelle(),
                e.getJours(),
                e.getHeureDebut(),
                e.getHeureFin(),
                e.getRole(),
                e.getNombreRequis(),
                e.getSiteId(),
                e.getDateDebut(),
                e.getDateFin(),
                e.getPriorite()
        );
    }
}

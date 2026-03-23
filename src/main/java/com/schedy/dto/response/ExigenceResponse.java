package com.schedy.dto.response;

import com.schedy.entity.Exigence;

import java.util.List;

public record ExigenceResponse(
        String id,
        String libelle,
        List<Integer> jours,
        double heureDebut,
        double heureFin,
        String role,
        int nombreRequis,
        String siteId
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
                e.getSiteId()
        );
    }
}

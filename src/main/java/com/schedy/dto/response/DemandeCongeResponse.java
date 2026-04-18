package com.schedy.dto.response;

import com.schedy.entity.DemandeConge;
import com.schedy.entity.StatutDemande;

import java.time.LocalDate;

public record DemandeCongeResponse(
        String id,
        String employeId,
        String typeCongeId,
        LocalDate dateDebut,
        LocalDate dateFin,
        Double heureDebut,
        Double heureFin,
        double duree,
        StatutDemande statut,
        String motif,
        String noteApprobation,
        String organisationId
) {
    public static DemandeCongeResponse from(DemandeConge d) {
        return new DemandeCongeResponse(
                d.getId(),
                d.getEmployeId(),
                d.getTypeCongeId(),
                d.getDateDebut(),
                d.getDateFin(),
                d.getHeureDebut(),
                d.getHeureFin(),
                d.getDuree(),
                d.getStatut(),
                d.getMotif(),
                d.getNoteApprobation(),
                d.getOrganisationId()
        );
    }
}

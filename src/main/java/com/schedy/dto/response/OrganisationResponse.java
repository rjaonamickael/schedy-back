package com.schedy.dto.response;

import com.schedy.entity.Organisation;

public record OrganisationResponse(
        String id,
        String nom,
        String domaine,
        String adresse,
        String telephone,
        String pays,
        String dateRenouvellementConges
) {
    public static OrganisationResponse from(Organisation o) {
        return new OrganisationResponse(
                o.getId(),
                o.getNom(),
                o.getDomaine(),
                o.getAdresse(),
                o.getTelephone(),
                o.getPays(),
                o.getDateRenouvellementConges()
        );
    }
}

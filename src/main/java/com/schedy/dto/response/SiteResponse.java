package com.schedy.dto.response;

import com.schedy.entity.Site;

public record SiteResponse(
        String id,
        String nom,
        String adresse,
        String telephone,
        boolean actif,
        String organisationId
) {
    public static SiteResponse from(Site s) {
        return new SiteResponse(
                s.getId(),
                s.getNom(),
                s.getAdresse(),
                s.getTelephone(),
                s.isActif(),
                s.getOrganisationId()
        );
    }
}

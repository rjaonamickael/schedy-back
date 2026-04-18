package com.schedy.dto.response;

import com.schedy.entity.JourFerie;

import java.time.LocalDate;

public record JourFerieResponse(
        String id,
        String nom,
        LocalDate date,
        boolean recurrent,
        String siteId,
        String organisationId
) {
    public static JourFerieResponse from(JourFerie j) {
        return new JourFerieResponse(
                j.getId(),
                j.getNom(),
                j.getDate(),
                j.isRecurrent(),
                j.getSiteId(),
                j.getOrganisationId()
        );
    }
}

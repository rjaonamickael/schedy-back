package com.schedy.dto.response;

import com.schedy.entity.Pause;
import com.schedy.entity.SourcePause;
import com.schedy.entity.StatutPause;
import com.schedy.entity.TypePause;

import java.time.OffsetDateTime;

public record PauseResponse(
        String id,
        String employeId,
        String siteId,
        OffsetDateTime debut,
        OffsetDateTime fin,
        Integer dureeMinutes,
        TypePause type,
        SourcePause source,
        StatutPause statut,
        boolean payee,
        String confirmeParId,
        OffsetDateTime confirmeAt,
        String motifContestation
) {
    public static PauseResponse from(Pause p) {
        return new PauseResponse(
                p.getId(),
                p.getEmployeId(),
                p.getSiteId(),
                p.getDebut(),
                p.getFin(),
                p.getDureeMinutes(),
                p.getType(),
                p.getSource(),
                p.getStatut(),
                p.isPayee(),
                p.getConfirmeParId(),
                p.getConfirmeAt(),
                p.getMotifContestation()
        );
    }
}

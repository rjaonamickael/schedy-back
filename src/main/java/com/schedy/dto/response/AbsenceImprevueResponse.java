package com.schedy.dto.response;

import com.schedy.entity.AbsenceImprevue;
import com.schedy.entity.AbsenceImprevue.Initiateur;
import com.schedy.entity.StatutAbsenceImprevue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AbsenceImprevueResponse(
        String id,
        String employeId,
        LocalDate dateAbsence,
        String motif,
        String messageEmploye,
        String signalePar,
        Initiateur initiateur,
        Instant dateSignalement,
        StatutAbsenceImprevue statut,
        String valideParEmail,
        Instant dateValidation,
        String noteRefus,
        String noteManager,
        List<String> creneauIds,
        String siteId
) {
    /**
     * Maps an AbsenceImprevue entity to its response DTO.
     * organisationId is intentionally excluded — it is an internal tenant field
     * that must not be exposed through the public API contract.
     */
    public static AbsenceImprevueResponse from(AbsenceImprevue entity) {
        return new AbsenceImprevueResponse(
                entity.getId(),
                entity.getEmployeId(),
                entity.getDateAbsence(),
                entity.getMotif(),
                entity.getMessageEmploye(),
                entity.getSignalePar(),
                entity.getInitiateur(),
                entity.getDateSignalement(),
                entity.getStatut(),
                entity.getValideParEmail(),
                entity.getDateValidation(),
                entity.getNoteRefus(),
                entity.getNoteManager(),
                entity.getCreneauIds(),
                entity.getSiteId()
        );
    }
}

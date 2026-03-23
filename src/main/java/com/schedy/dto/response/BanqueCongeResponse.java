package com.schedy.dto.response;

import com.schedy.entity.BanqueConge;

import java.time.LocalDate;

public record BanqueCongeResponse(
        String id,
        String employeId,
        String typeCongeId,
        Double quota,
        double utilise,
        double enAttente,
        LocalDate dateDebut,
        LocalDate dateFin
) {
    public static BanqueCongeResponse from(BanqueConge b) {
        return new BanqueCongeResponse(
                b.getId(),
                b.getEmployeId(),
                b.getTypeCongeId(),
                b.getQuota(),
                b.getUtilise(),
                b.getEnAttente(),
                b.getDateDebut(),
                b.getDateFin()
        );
    }
}

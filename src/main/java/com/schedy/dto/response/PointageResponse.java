package com.schedy.dto.response;

import com.schedy.entity.MethodePointage;
import com.schedy.entity.Pointage;
import com.schedy.entity.StatutPointage;
import com.schedy.entity.TypePointage;

import java.time.OffsetDateTime;

public record PointageResponse(
        String id,
        String employeId,
        TypePointage type,
        OffsetDateTime horodatage,
        MethodePointage methode,
        StatutPointage statut,
        String anomalie,
        String siteId
) {
    public static PointageResponse from(Pointage p) {
        return new PointageResponse(
                p.getId(),
                p.getEmployeId(),
                p.getType(),
                p.getHorodatage(),
                p.getMethode(),
                p.getStatut(),
                p.getAnomalie(),
                p.getSiteId()
        );
    }
}

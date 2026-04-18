package com.schedy.dto.response;

import com.schedy.entity.CreneauAssigne;

public record CreneauAssigneResponse(
        String id,
        String employeId,
        int jour,
        double heureDebut,
        double heureFin,
        String semaine,
        String siteId,
        String role,
        boolean publie
) {
    public static CreneauAssigneResponse from(CreneauAssigne c) {
        return new CreneauAssigneResponse(
                c.getId(),
                c.getEmployeId(),
                c.getJour(),
                c.getHeureDebut(),
                c.getHeureFin(),
                c.getSemaine(),
                c.getSiteId(),
                c.getRole(),
                c.isPublie()
        );
    }
}

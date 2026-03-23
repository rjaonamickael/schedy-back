package com.schedy.dto.response;

import com.schedy.entity.Parametres;

import java.util.List;

public record ParametresResponse(
        Long id,
        int heureDebut,
        int heureFin,
        List<Integer> joursActifs,
        int premierJour,
        double dureeMinAffectation,
        double heuresMaxSemaine,
        String siteId,
        String taillePolice,
        String planningVue,
        double planningGranularite,
        List<String> reglesAffectation
) {
    public static ParametresResponse from(Parametres p) {
        return new ParametresResponse(
                p.getId(),
                p.getHeureDebut(),
                p.getHeureFin(),
                p.getJoursActifs(),
                p.getPremierJour(),
                p.getDureeMinAffectation(),
                p.getHeuresMaxSemaine(),
                p.getSiteId(),
                p.getTaillePolice(),
                p.getPlanningVue(),
                p.getPlanningGranularite(),
                p.getReglesAffectation()
        );
    }
}

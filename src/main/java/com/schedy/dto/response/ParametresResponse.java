package com.schedy.dto.response;

import com.schedy.entity.Parametres;
import com.schedy.entity.ReglePause;

import java.util.List;

public record ParametresResponse(
        Long id,
        int heureDebut,
        int heureFin,
        List<Integer> joursActifs,
        int premierJour,
        double dureeMinAffectation,
        double heuresMaxSemaine,
        double dureeMaxJour,
        String siteId,
        String taillePolice,
        String planningVue,
        double planningGranularite,
        List<String> reglesAffectation,
        Integer delaiSignalementAbsenceMinutes,
        Integer seuilAbsenceVsCongeHeures,

        // Labor law constraints
        Double reposMinEntreShifts,
        Double reposHebdoMin,
        Integer maxJoursConsecutifs,

        // Pause Layer 1: fixed collective
        Double pauseFixeHeureDebut,
        Double pauseFixeHeureFin,
        List<Integer> pauseFixeJours,

        // Pause Layer 2: tiered rules
        Boolean pauseAvancee,
        Double pauseSeuilHeures,
        Integer pauseDureeMinutes,
        Boolean pausePayee,
        List<ReglePause> reglesPause,

        // Pause Layer 3: detection window
        Integer fenetrePauseMinMinutes,
        Integer fenetrePauseMaxMinutes,
        Boolean pauseRenoncementAutorise
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
                p.getDureeMaxJour() != null ? p.getDureeMaxJour() : 10.0,
                p.getSiteId(),
                p.getTaillePolice(),
                p.getPlanningVue(),
                p.getPlanningGranularite(),
                p.getReglesAffectation(),
                p.getDelaiSignalementAbsenceMinutes(),
                p.getSeuilAbsenceVsCongeHeures(),
                // Labor law
                p.getReposMinEntreShifts(),
                p.getReposHebdoMin(),
                p.getMaxJoursConsecutifs(),
                // Pause Layer 1
                p.getPauseFixeHeureDebut(),
                p.getPauseFixeHeureFin(),
                p.getPauseFixeJours(),
                // Pause Layer 2
                p.getPauseAvancee(),
                p.getPauseSeuilHeures(),
                p.getPauseDureeMinutes(),
                p.getPausePayee(),
                p.getReglesPause(),
                // Pause Layer 3
                p.getFenetrePauseMinMinutes(),
                p.getFenetrePauseMaxMinutes(),
                p.getPauseRenoncementAutorise()
        );
    }
}

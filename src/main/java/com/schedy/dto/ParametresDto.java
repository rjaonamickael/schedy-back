package com.schedy.dto;

import com.schedy.entity.ReglePause;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ParametresDto(
    Long id,
    @Min(0) @Max(23) int heureDebut,
    @Min(1) @Max(24) int heureFin,
    List<Integer> joursActifs,
    @Min(0) @Max(6) int premierJour,
    @DecimalMin("0.25") double dureeMinAffectation,
    @DecimalMin("1") @DecimalMax("168") double heuresMaxSemaine,
    @DecimalMin("1") @DecimalMax("24") Double dureeMaxJour,
    @Size(max = 50) String taillePolice,
    @Size(max = 50) String planningVue,
    @DecimalMin("0.25") @DecimalMax("4") double planningGranularite,
    List<String> reglesAffectation,
    @Min(0) @Max(1440) Integer delaiSignalementAbsenceMinutes,
    @Min(0) @Max(720) Integer seuilAbsenceVsCongeHeures,

    // Labor law constraints
    @DecimalMin("0") @DecimalMax("24") Double reposMinEntreShifts,
    @DecimalMin("0") @DecimalMax("168") Double reposHebdoMin,
    @Min(0) @Max(7) Integer maxJoursConsecutifs,

    // Pause Layer 1: fixed collective
    @DecimalMin("0") @DecimalMax("24") Double pauseFixeHeureDebut,
    @DecimalMin("0") @DecimalMax("24") Double pauseFixeHeureFin,
    List<Integer> pauseFixeJours,

    // Pause Layer 2: tiered rules
    Boolean pauseAvancee,
    @DecimalMin("0") @DecimalMax("24") Double pauseSeuilHeures,
    @Min(0) @Max(480) Integer pauseDureeMinutes,
    Boolean pausePayee,
    List<ReglePause> reglesPause,

    // Pause Layer 3: detection window
    @Min(0) @Max(480) Integer fenetrePauseMinMinutes,
    @Min(0) @Max(480) Integer fenetrePauseMaxMinutes,
    Boolean pauseRenoncementAutorise
) {}

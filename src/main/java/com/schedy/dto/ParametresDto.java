package com.schedy.dto;

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
    @Min(0.25) double dureeMinAffectation,
    @Min(1) @Max(168) double heuresMaxSemaine,
    @Size(max = 50) String taillePolice,
    @Size(max = 50) String planningVue,
    @Min(0.25) @Max(4) double planningGranularite,
    List<String> reglesAffectation
) {}

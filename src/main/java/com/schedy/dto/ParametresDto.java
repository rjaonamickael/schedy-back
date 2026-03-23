package com.schedy.dto;

import java.util.List;

public record ParametresDto(
    Long id,
    int heureDebut,
    int heureFin,
    List<Integer> joursActifs,
    int premierJour,
    double dureeMinAffectation,
    String taillePolice,
    String planningVue,
    double planningGranularite,
    List<String> reglesAffectation
) {}

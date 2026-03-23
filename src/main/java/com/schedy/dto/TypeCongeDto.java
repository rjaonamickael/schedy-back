package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;

public record TypeCongeDto(
    String id,
    @NotBlank String nom,
    @NotBlank String categorie,
    @NotBlank String unite,
    String couleur,
    String modeQuota,
    boolean quotaIllimite,
    boolean autoriserNegatif,
    Double accrualMontant,
    String accrualFrequence,
    Double reportMax,
    Integer reportDuree,
    String organisationId
) {}

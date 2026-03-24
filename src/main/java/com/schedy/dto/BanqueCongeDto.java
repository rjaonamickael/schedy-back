package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record BanqueCongeDto(
    String id,
    @NotBlank String employeId,
    @NotBlank String typeCongeId,
    Double quota,
    double utilise,
    double enAttente,
    LocalDate dateDebut,
    LocalDate dateFin,
    String organisationId
) {}

package com.schedy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DemandeCongeDto(
    String id,
    @NotBlank String employeId,
    @NotBlank String typeCongeId,
    @NotNull LocalDate dateDebut,
    @NotNull LocalDate dateFin,
    Double heureDebut,
    Double heureFin,
    @Min(0) double duree,
    String statut,
    @Size(max = 500) String motif,
    @Size(max = 500) String noteApprobation,
    String organisationId
) {}

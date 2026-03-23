package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DemandeCongeDto(
    String id,
    @NotBlank String employeId,
    @NotBlank String typeCongeId,
    @NotNull LocalDate dateDebut,
    @NotNull LocalDate dateFin,
    Double heureDebut,
    Double heureFin,
    double duree,
    String statut,
    String motif,
    String noteApprobation,
    String organisationId
) {}

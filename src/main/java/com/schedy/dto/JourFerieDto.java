package com.schedy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record JourFerieDto(
    String id,
    @NotBlank String nom,
    @NotNull LocalDate date,
    boolean recurrent
) {}

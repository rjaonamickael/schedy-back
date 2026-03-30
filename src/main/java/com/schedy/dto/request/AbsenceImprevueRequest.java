package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record AbsenceImprevueRequest(
    @NotBlank(message = "employeId est obligatoire")
    String employeId,

    @NotNull(message = "dateAbsence est obligatoire")
    LocalDate dateAbsence,

    @NotBlank(message = "motif est obligatoire")
    String motif,

    String messageEmploye,

    List<String> creneauIds
) {}

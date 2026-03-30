package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReassignerCreneauRequest(
    @NotBlank(message = "absenceImprevueId est obligatoire")
    String absenceImprevueId,

    @NotBlank(message = "creneauId est obligatoire")
    String creneauId,

    @NotBlank(message = "remplacantId est obligatoire")
    String remplacantId
) {}

package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record PointageManuelRequest(
        @NotBlank String employeId,
        String siteId,
        @NotBlank String methode,
        @NotBlank String type,
        @NotNull OffsetDateTime horodatage
) {}

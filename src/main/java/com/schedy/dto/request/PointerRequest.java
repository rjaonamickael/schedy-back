package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PointerRequest(
    @NotBlank String employeId,
    String siteId,
    @NotBlank String methode
) {}

package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ValidatePointageCodeRequest(
    @NotBlank String employeId,
    @NotBlank String code
) {}

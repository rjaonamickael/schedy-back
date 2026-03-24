package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FindByPinRequest(
    @NotBlank @Size(min = 4, max = 10) String pin
) {}

package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrgStatusRequest(
    @NotBlank String status
) {}

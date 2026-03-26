package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AnnouncementDto(
    @NotBlank String title,
    @NotBlank String body,
    String severity,
    boolean active,
    String expiresAt
) {}

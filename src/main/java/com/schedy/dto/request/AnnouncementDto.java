package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public record AnnouncementDto(
    @NotBlank String title,
    @NotBlank String body,
    String severity,
    boolean active,
    OffsetDateTime expiresAt
) {}

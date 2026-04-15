package com.schedy.dto.request;

import com.schedy.entity.TypePause;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Manager-initiated pause entry.
 *
 * <p>Used when a manager wants to record a pause that was not captured by
 * the automatic Layer 3 detection — typically when the gap between two
 * pointages fell outside the configured window, when pointages were missed,
 * or when a long shift needs an explicit break recorded for payroll.
 *
 * <p>The resulting {@code Pause} is created with
 * {@code source = MANUEL, statut = CONFIRME} since it reflects a deliberate
 * manager decision, bypassing the detect → confirm workflow.
 */
public record PauseCreateRequest(
        @NotBlank String employeId,
        String siteId,
        @NotNull OffsetDateTime debut,
        @NotNull OffsetDateTime fin,
        @NotNull TypePause type,
        boolean payee
) {}

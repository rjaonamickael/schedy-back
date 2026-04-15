package com.schedy.dto.response;

import java.time.OffsetDateTime;

/**
 * V36: one entry on a printable PIN sheet. The frontend renders one card per
 * entry, formatted as a credit-card sized rectangle (85x55 mm) with the
 * employee name, primary site, PIN, generation date, and version. Plain-text
 * PIN is included because the print view is the very purpose of this DTO —
 * never expose this outside the print flow.
 *
 * <p>V37: added {@code rolePrincipal} so the frontend can group cards by
 * (site → role → name) for the bulk print sheet. Index-0 of the multi-role
 * list (per Sprint 16 hierarchy).
 */
public record PinSheetEntryResponse(
        String employeId,
        String nom,
        String siteNomPrincipal,
        String rolePrincipal,
        String pinClair,
        OffsetDateTime pinGeneratedAt,
        Integer pinVersion
) {}

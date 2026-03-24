package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

// B-14: siteId removed — the admin code is global per deployment, not per-site.
// The controller only needs the code to validate against schedy.kiosk.admin-code.
public record ValidateKioskAdminRequest(
    @NotBlank String code
) {}

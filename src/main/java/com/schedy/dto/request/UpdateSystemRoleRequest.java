package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateSystemRoleRequest(
    @NotBlank String systemRole,  // "MANAGER" or "EMPLOYEE"
    String tempPassword           // required when creating a new User account for promotion
) {}

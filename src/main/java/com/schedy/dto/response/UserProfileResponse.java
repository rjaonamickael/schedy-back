package com.schedy.dto.response;

public record UserProfileResponse(
    String email,
    String role,
    String nom,
    String organisationId,
    String employeId
) {}

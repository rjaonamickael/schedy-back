package com.schedy.dto.response;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String email,
    String role,
    String employeId,
    String organisationId,
    String pays
) {}

package com.schedy.dto.response;

public record ImpersonateResponse(
    String accessToken,
    String organisationName,
    long expiresIn
) {}

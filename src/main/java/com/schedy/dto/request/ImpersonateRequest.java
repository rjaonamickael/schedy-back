package com.schedy.dto.request;

public record ImpersonateRequest(
    String reason,
    String totpCode
) {}

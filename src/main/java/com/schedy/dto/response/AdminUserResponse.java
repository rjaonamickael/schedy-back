package com.schedy.dto.response;

public record AdminUserResponse(
    Long id,
    String email,
    String role,
    String nom,
    boolean passwordSet,
    String employeId
) {}

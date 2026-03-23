package com.schedy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    String role,
    String employeId,
    String organisationId
) {}

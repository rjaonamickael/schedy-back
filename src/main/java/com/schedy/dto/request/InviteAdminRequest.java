package com.schedy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteAdminRequest(
    @NotBlank @Email String email,
    String nom
) {}

package com.schedy.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "Le nom ne peut pas depasser 100 caracteres")
    String nom
) {}

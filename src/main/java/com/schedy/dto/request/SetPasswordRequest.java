package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 128)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
             message = "Le mot de passe doit contenir au moins 8 caracteres, 1 majuscule, 1 minuscule, 1 chiffre et 1 caractere special")
    String password
) {}

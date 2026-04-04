package com.schedy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
        @Size(min = 8, max = 128, message = "Le mot de passe doit contenir entre 8 et 128 caracteres")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
            message = "Le mot de passe doit contenir au moins une lettre, un chiffre et un caractere special")
        String newPassword
) {}

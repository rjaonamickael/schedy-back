package com.schedy.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH-style payload for the authenticated user's own profile.
 * {@code null} = champ non modifie. Chaine vide = clear explicite pour les
 * champs nullable.
 */
public record UpdateProfileRequest(
    @Size(max = 100, message = "Le nom ne peut pas depasser 100 caracteres")
    String nom,

    // V49 — LinkedIn profile perso. Utilise par le snapshot temoignage.
    @Size(max = 500, message = "linkedinUrl: max 500 caracteres")
    @Pattern(
        regexp = "^$|^https://([a-z]{2,3}\\.)?linkedin\\.com/.+",
        message = "L'URL LinkedIn doit commencer par https://...linkedin.com/"
    )
    String linkedinUrl
) {}

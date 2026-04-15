package com.schedy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH-style update payload for an admin's own organisation profile.
 * All fields are optional; null means "do not touch this field". Empty
 * strings are accepted as explicit clears for nullable columns.
 */
public record UpdateOrgProfileRequest(
        @Size(max = 255, message = "nom: max 255 caracteres") String nom,
        @Size(max = 255, message = "domaine: max 255 caracteres") String domaine,
        @Size(max = 500, message = "adresse: max 500 caracteres") String adresse,
        @Size(max = 30, message = "telephone: max 30 caracteres") String telephone,
        @Pattern(regexp = "^[A-Z]{3}$", message = "pays: code ISO alpha-3 (CAN, MDG, ...)") String pays,
        @Size(max = 10, message = "province: max 10 caracteres") String province,
        @Size(max = 50, message = "businessNumber: max 50 caracteres") String businessNumber,
        @Size(max = 50, message = "provincialId: max 50 caracteres") String provincialId,
        @Size(max = 50, message = "nif: max 50 caracteres") String nif,
        @Size(max = 50, message = "stat: max 50 caracteres") String stat,
        @Size(max = 255, message = "legalRepresentative: max 255 caracteres") String legalRepresentative,
        @Email(message = "contactEmail: format invalide")
        @Size(max = 255, message = "contactEmail: max 255 caracteres") String contactEmail,
        @Size(max = 20, message = "siret: max 20 caracteres") String siret
) {}

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
        @Size(max = 20, message = "siret: max 20 caracteres") String siret,

        // V48 — brand / social presence. Logo upload via multipart endpoint separe
        // POST /api/v1/organisation/me/logo (pas ici).
        @Size(max = 500, message = "websiteUrl: max 500 caracteres")
        @Pattern(
            regexp = "^$|^https?://.+\\..+",
            message = "L'URL du site web doit commencer par http:// ou https://"
        ) String websiteUrl,

        @Size(max = 500, message = "linkedinUrl: max 500 caracteres")
        @Pattern(
            regexp = "^$|^https://([a-z]{2,3}\\.)?linkedin\\.com/.+",
            message = "L'URL LinkedIn doit commencer par https://...linkedin.com/"
        ) String linkedinUrl,

        // V50 — restauration Facebook / Instagram / X (Twitter) entreprise.
        @Size(max = 500, message = "facebookUrl: max 500 caracteres")
        @Pattern(
            regexp = "^$|^https?://([a-z0-9.-]+\\.)?(facebook|fb)\\.com/.+",
            message = "L'URL Facebook doit commencer par https://...facebook.com/ ou https://fb.com/"
        ) String facebookUrl,

        @Size(max = 500, message = "instagramUrl: max 500 caracteres")
        @Pattern(
            regexp = "^$|^https?://([a-z0-9.-]+\\.)?instagram\\.com/.+",
            message = "L'URL Instagram doit commencer par https://...instagram.com/"
        ) String instagramUrl,

        @Size(max = 500, message = "twitterUrl: max 500 caracteres")
        @Pattern(
            regexp = "^$|^https?://([a-z0-9.-]+\\.)?(x|twitter)\\.com/.+",
            message = "L'URL X doit commencer par https://x.com/ ou https://twitter.com/"
        ) String twitterUrl
) {}

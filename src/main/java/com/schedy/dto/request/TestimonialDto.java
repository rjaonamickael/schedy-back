package com.schedy.dto.request;

import jakarta.validation.constraints.*;

/**
 * Inbound DTO for an org admin submitting a testimonial.
 * The organisation context is resolved from TenantContext — never sent by the client.
 *
 * <p>The logo URL, if any, must have been returned by the separate
 * {@code POST /testimonials/logo} upload endpoint — the client does NOT
 * upload file bytes on the main submit.
 */
public record TestimonialDto(

    @NotBlank(message = "Le nom de l'auteur est obligatoire.")
    @Size(max = 100, message = "Le nom de l'auteur ne peut pas dépasser 100 caractères.")
    String authorName,

    @NotBlank(message = "Le rôle de l'auteur est obligatoire.")
    @Size(max = 100, message = "Le rôle de l'auteur ne peut pas dépasser 100 caractères.")
    String authorRole,

    /** Optional — city or region. */
    @Size(max = 100, message = "La ville ne peut pas dépasser 100 caractères.")
    String authorCity,

    @NotBlank(message = "Le témoignage est obligatoire.")
    @Size(max = 300, message = "Le témoignage ne peut pas dépasser 300 caractères.")
    String quote,

    /** Optional catchy headline shown bold above the quote on the public card. */
    @Size(max = 100, message = "Le titre ne peut pas dépasser 100 caractères.")
    String quoteTitle,

    @Min(value = 1, message = "La note minimale est 1.")
    @Max(value = 5, message = "La note maximale est 5.")
    int stars,

    @NotBlank(message = "La langue est obligatoire.")
    @Pattern(regexp = "^(fr|en)$", message = "La langue doit être 'fr' ou 'en'.")
    String language,

    /**
     * LinkedIn profile URL. Optional. Must start with {@code https://} and
     * point to a linkedin.com host.
     */
    @Size(max = 500, message = "L'URL LinkedIn ne peut pas dépasser 500 caractères.")
    @Pattern(
        regexp = "^$|^https://([a-z]{2,3}\\.)?linkedin\\.com/.+",
        message = "L'URL LinkedIn doit commencer par https://…linkedin.com/"
    )
    String linkedinUrl,

    /** Organisation website. Optional. Must start with http(s):// */
    @Size(max = 500, message = "L'URL du site web ne peut pas dépasser 500 caractères.")
    @Pattern(
        regexp = "^$|^https?://.+\\..+",
        message = "L'URL du site web doit commencer par http:// ou https://"
    )
    String websiteUrl,

    /** Facebook page / profile. Optional. */
    @Size(max = 500, message = "L'URL Facebook ne peut pas dépasser 500 caractères.")
    @Pattern(
        regexp = "^$|^https?://([a-z0-9.-]+\\.)?(facebook|fb)\\.com/.+",
        message = "L'URL Facebook doit commencer par https://…facebook.com/ ou https://fb.com/"
    )
    String facebookUrl,

    /** Instagram profile. Optional. */
    @Size(max = 500, message = "L'URL Instagram ne peut pas dépasser 500 caractères.")
    @Pattern(
        regexp = "^$|^https?://([a-z0-9.-]+\\.)?instagram\\.com/.+",
        message = "L'URL Instagram doit commencer par https://…instagram.com/"
    )
    String instagramUrl,

    /** X (Twitter) profile. Accepts both {@code x.com} and {@code twitter.com}. Optional. */
    @Size(max = 500, message = "L'URL X (Twitter) ne peut pas dépasser 500 caractères.")
    @Pattern(
        regexp = "^$|^https?://([a-z0-9.-]+\\.)?(x|twitter)\\.com/.+",
        message = "L'URL X doit commencer par https://x.com/ ou https://twitter.com/"
    )
    String twitterUrl,

    /**
     * Public URL of the sanitized logo returned by the upload endpoint.
     * Server-side, we verify this URL starts with our R2 public base before
     * persisting — a client cannot point {@code logoUrl} at an arbitrary
     * third-party host.
     */
    @Size(max = 500, message = "L'URL du logo ne peut pas dépasser 500 caractères.")
    String logoUrl,

    @Size(max = 500, message = "La section \"Problème\" ne peut pas dépasser 500 caractères.")
    String textProbleme,

    @Size(max = 500, message = "La section \"Solution\" ne peut pas dépasser 500 caractères.")
    String textSolution,

    @Size(max = 500, message = "La section \"Impact\" ne peut pas dépasser 500 caractères.")
    String textImpact

) {}

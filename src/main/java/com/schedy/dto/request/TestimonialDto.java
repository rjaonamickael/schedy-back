package com.schedy.dto.request;

import jakarta.validation.constraints.*;

/**
 * Inbound DTO for an org admin submitting a testimonial.
 * The organisation context is resolved from TenantContext — never sent by the client.
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
    @Size(max = 500, message = "Le témoignage ne peut pas dépasser 500 caractères.")
    String quote,

    @Min(value = 1, message = "La note minimale est 1.")
    @Max(value = 5, message = "La note maximale est 5.")
    int stars,

    @NotBlank(message = "La langue est obligatoire.")
    @Pattern(regexp = "^(fr|en)$", message = "La langue doit être 'fr' ou 'en'.")
    String language

) {}

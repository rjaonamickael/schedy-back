package com.schedy.dto.request;

import jakarta.validation.constraints.*;

/**
 * Inbound DTO for an org admin submitting a testimonial.
 *
 * <p>V48 refactor : logo, website et LinkedIn (entreprise + auteur) sont
 * DERIVES SERVEUR au submit :
 * <ul>
 *   <li>{@code Organisation.logoUrl}, {@code Organisation.websiteUrl},
 *       {@code Organisation.linkedinUrl} → copies vers les snapshot
 *       correspondants de {@link com.schedy.entity.Testimonial}.</li>
 *   <li>{@code User.linkedinUrl}, {@code User.photoUrl} → copies vers
 *       {@code Testimonial.linkedinUrl} (LinkedIn perso auteur) et
 *       {@code Testimonial.authorPhotoUrl}.</li>
 * </ul>
 *
 * <p>Le client n'envoie PLUS les champs logoUrl/linkedinUrl/websiteUrl/
 * facebookUrl/instagramUrl/twitterUrl : ils sont soit derives serveur
 * (les 3 premiers), soit supprimes du modele (Facebook/Instagram/Twitter
 * retires en V48, &lt;5% usage B2B pro).
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

    @Size(max = 500, message = "La section \"Problème\" ne peut pas dépasser 500 caractères.")
    String textProbleme,

    @Size(max = 500, message = "La section \"Solution\" ne peut pas dépasser 500 caractères.")
    String textSolution,

    @Size(max = 500, message = "La section \"Impact\" ne peut pas dépasser 500 caractères.")
    String textImpact

) {}

package com.schedy.dto.response;

import com.schedy.entity.Testimonial;

import java.time.OffsetDateTime;

/**
 * Full projection of a Testimonial.
 * Used by both the public endpoint (status=APPROVED subset) and the superadmin list.
 * organisationName is nullable — populated from a joined Organisation when available.
 *
 * <p>V48 refactor :
 * <ul>
 *   <li>Suppression de {@code facebookUrl}, {@code instagramUrl}, {@code twitterUrl}
 *       (moins de 5% usage B2B pro, validation Round 1 experts).</li>
 *   <li>Ajout {@code authorPhotoUrl} (snapshot User.photoUrl) et
 *       {@code organisationLinkedinUrl} (snapshot Organisation.linkedinUrl).</li>
 *   <li>{@code linkedinUrl} porte maintenant exclusivement le LinkedIn PERSONNEL
 *       de l'auteur (snapshote de User.linkedinUrl au submit) — affichage footer.</li>
 *   <li>{@code organisationLinkedinUrl} porte le LinkedIn ENTREPRISE — affichage header.</li>
 * </ul>
 */
public record TestimonialResponse(

    String id,
    String organisationId,
    String organisationName,
    String authorName,
    String authorRole,
    String authorCity,
    String quote,
    String quoteTitle,
    int stars,
    String language,
    String status,
    int displayOrder,
    OffsetDateTime createdAt,
    OffsetDateTime reviewedAt,
    String reviewedBy,
    // Snapshots at submit time
    String linkedinUrl,               // author personal LinkedIn
    String organisationLinkedinUrl,   // V48 — org LinkedIn (header)
    String websiteUrl,                // org website
    String logoUrl,                   // org logo (R2)
    String authorPhotoUrl,            // V48 — author personal photo (R2)
    // V50 — restauration snapshots reseaux sociaux entreprise
    String facebookUrl,
    String instagramUrl,
    String twitterUrl,
    String textProbleme,
    String textSolution,
    String textImpact,
    // V44 subscription tier stamped at submit time (ESSENTIALS / STARTER / PRO or null)
    String planTier

) {
    /** Factory that does not require an org name lookup (public endpoint). */
    public static TestimonialResponse from(Testimonial entity) {
        return from(entity, null);
    }

    /** Factory with an optional organisation name (superadmin / org endpoints). */
    public static TestimonialResponse from(Testimonial entity, String organisationName) {
        return new TestimonialResponse(
            entity.getId(),
            entity.getOrganisationId(),
            organisationName,
            entity.getAuthorName(),
            entity.getAuthorRole(),
            entity.getAuthorCity(),
            entity.getQuote(),
            entity.getQuoteTitle(),
            entity.getStars(),
            entity.getLanguage(),
            entity.getStatus().name(),
            entity.getDisplayOrder(),
            entity.getCreatedAt(),
            entity.getReviewedAt(),
            entity.getReviewedBy(),
            entity.getLinkedinUrl(),
            entity.getOrganisationLinkedinUrl(),
            entity.getWebsiteUrl(),
            entity.getLogoUrl(),
            entity.getAuthorPhotoUrl(),
            entity.getFacebookUrl(),
            entity.getInstagramUrl(),
            entity.getTwitterUrl(),
            entity.getTextProbleme(),
            entity.getTextSolution(),
            entity.getTextImpact(),
            entity.getPlanTier() != null ? entity.getPlanTier().name() : null
        );
    }
}

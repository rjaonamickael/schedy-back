package com.schedy.dto.response;

import com.schedy.entity.Testimonial;

import java.time.OffsetDateTime;

/**
 * Full projection of a Testimonial.
 * Used by both the public endpoint (status=APPROVED subset) and the superadmin list.
 * organisationName is nullable — populated from a joined Organisation when available.
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
    // V41 rich fields
    String linkedinUrl,
    String websiteUrl,
    String logoUrl,
    String textProbleme,
    String textSolution,
    String textImpact,
    // V42 social links
    String facebookUrl,
    String instagramUrl,
    String twitterUrl,
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
            entity.getWebsiteUrl(),
            entity.getLogoUrl(),
            entity.getTextProbleme(),
            entity.getTextSolution(),
            entity.getTextImpact(),
            entity.getFacebookUrl(),
            entity.getInstagramUrl(),
            entity.getTwitterUrl(),
            entity.getPlanTier() != null ? entity.getPlanTier().name() : null
        );
    }
}

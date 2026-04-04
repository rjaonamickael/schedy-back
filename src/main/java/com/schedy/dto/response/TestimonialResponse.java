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
    int stars,
    String language,
    String status,
    int displayOrder,
    OffsetDateTime createdAt,
    OffsetDateTime reviewedAt,
    String reviewedBy

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
            entity.getStars(),
            entity.getLanguage(),
            entity.getStatus().name(),
            entity.getDisplayOrder(),
            entity.getCreatedAt(),
            entity.getReviewedAt(),
            entity.getReviewedBy()
        );
    }
}

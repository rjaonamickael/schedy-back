package com.schedy.dto.response;

public record UserProfileResponse(
    String email,
    String role,
    String nom,
    String organisationId,
    String organisationName,
    String employeId,
    /** Current subscription tier of the user's org — drives client-side features like
        the testimonial preview plan badge. Null when the org has no subscription row. */
    String planTier
) {}

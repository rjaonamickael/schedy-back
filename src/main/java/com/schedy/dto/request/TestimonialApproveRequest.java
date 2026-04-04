package com.schedy.dto.request;

/**
 * Body for superadmin approve action.
 * displayOrder defaults to 0 if not provided; the caller can specify
 * an explicit order to position the testimonial in the public carousel.
 */
public record TestimonialApproveRequest(
    int displayOrder
) {}

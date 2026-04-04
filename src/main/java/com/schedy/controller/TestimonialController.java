package com.schedy.controller;

import com.schedy.dto.request.TestimonialDto;
import com.schedy.dto.response.TestimonialResponse;
import com.schedy.service.TestimonialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authenticated org-admin endpoints for testimonial management.
 *
 *   POST /api/v1/testimonials      — submit a new testimonial (status = PENDING)
 *   GET  /api/v1/testimonials/mine — list all testimonials submitted by the caller's org
 */
@RestController
@RequestMapping("/api/v1/testimonials")
@RequiredArgsConstructor
public class TestimonialController {

    private final TestimonialService testimonialService;

    /**
     * POST /api/v1/testimonials
     * Submit a testimonial on behalf of the authenticated organisation.
     * Returns 201 with the created testimonial in PENDING status.
     * Returns 409 when a PENDING testimonial already exists for this org.
     */
    @PostMapping
    public ResponseEntity<TestimonialResponse> submit(
            @Valid @RequestBody TestimonialDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(testimonialService.submit(dto));
    }

    /**
     * GET /api/v1/testimonials/mine
     * Returns all testimonials submitted by the authenticated organisation,
     * ordered by createdAt descending (most recent first).
     */
    @GetMapping("/mine")
    public ResponseEntity<List<TestimonialResponse>> getMyTestimonials() {
        return ResponseEntity.ok(testimonialService.getMyTestimonials());
    }
}

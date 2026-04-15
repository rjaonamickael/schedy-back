package com.schedy.controller;

import com.schedy.dto.request.TestimonialDto;
import com.schedy.dto.response.TestimonialResponse;
import com.schedy.service.R2StorageService;
import com.schedy.service.TestimonialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Authenticated org-admin endpoints for testimonial management.
 *
 *   POST /api/v1/testimonials       — submit a new testimonial (status = PENDING)
 *   GET  /api/v1/testimonials/mine  — list all testimonials submitted by the caller's org
 *   POST /api/v1/testimonials/logo  — upload + sanitize an SVG logo, returns its public URL
 */
@RestController
@RequestMapping("/api/v1/testimonials")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TestimonialController {

    private final TestimonialService testimonialService;
    private final R2StorageService r2StorageService;

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
     * PUT /api/v1/testimonials/{id}
     * Update an existing testimonial owned by the caller's organisation.
     * Editing an APPROVED or REJECTED testimonial resets its status to
     * PENDING so the superadmin can re-moderate the new content.
     * Returns 404 when the testimonial is not found OR belongs to another org.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TestimonialResponse> update(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @Valid @RequestBody TestimonialDto dto) {
        return ResponseEntity.ok(testimonialService.update(id, dto));
    }

    /**
     * DELETE /api/v1/testimonials/{id}
     * Permanently deletes a testimonial owned by the caller's organisation.
     * No superadmin approval is required — orgs own their testimonials end
     * to end. The R2 logo, if any, is cleaned up best-effort server-side.
     * Returns 204 No Content on success, or 404 when the row is unknown or
     * belongs to another organisation (silent enumeration defense).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @org.springframework.web.bind.annotation.PathVariable String id) {
        testimonialService.delete(id);
        return ResponseEntity.noContent().build();
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

    /**
     * POST /api/v1/testimonials/logo (multipart/form-data)
     * Upload a logo for the testimonial in progress. The file MUST be a
     * valid SVG — the content is sanitized by {@link com.schedy.util.SvgSanitizer}
     * before it's written to R2. The response body contains a single
     * {@code url} property that the frontend passes back in the main
     * {@code TestimonialDto.logoUrl} field on submit.
     *
     * <p>Returns {@link CompletableFuture} so Spring MVC releases the
     * servlet worker thread while R2 acknowledges the PUT. Validation and
     * SVG sanitization happen synchronously before the future is returned
     * — any {@link com.schedy.exception.BusinessRuleException} surfaces
     * as a regular 422 without ever touching the async path.
     */
    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    public CompletableFuture<ResponseEntity<Map<String, String>>> uploadLogo(
            @RequestParam("file") MultipartFile file) {
        return r2StorageService.uploadTestimonialLogoAsync(file)
                .thenApply(publicUrl -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(Map.of("url", publicUrl)));
    }
}

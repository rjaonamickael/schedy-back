package com.schedy.controller;

import com.schedy.dto.request.RegistrationRequestDto;
import com.schedy.dto.request.WaitlistJoinRequest;
import com.schedy.dto.response.RegistrationRequestResponse;
import com.schedy.dto.response.TestimonialResponse;
import com.schedy.service.ProWaitlistService;
import com.schedy.service.RegistrationRequestService;
import com.schedy.service.TestimonialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Unauthenticated public endpoints.
 * Mapped under /api/v1/public/** which is permitAll in SecurityConfig.
 *
 * Endpoints:
 *   POST /api/v1/public/registration-requests — organisation sign-up request
 *   POST /api/v1/public/waitlist/join         — PRO plan waitlist sign-up
 *   GET  /api/v1/public/testimonials          — approved testimonials for landing page
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicRegistrationController {

    private final RegistrationRequestService registrationRequestService;
    private final ProWaitlistService         proWaitlistService;
    private final TestimonialService         testimonialService;

    /**
     * POST /api/v1/public/registration-requests
     * Submit a new organisation registration request.
     * Returns 201 with a confirmation message on success.
     * Returns 409 if a PENDING request already exists for the same email.
     */
    @PostMapping("/registration-requests")
    public ResponseEntity<Map<String, Object>> submitRegistrationRequest(
            @Valid @RequestBody RegistrationRequestDto dto) {

        RegistrationRequestResponse created = registrationRequestService.submitRequest(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message",
            "Votre demande a bien été reçue. "
            + "Nous l'examinerons dans les meilleurs délais et vous contacterons par email. / "
            + "Your request has been received. "
            + "We will review it and contact you by email shortly.",
            "requestId", created.id()
        ));
    }

    /**
     * POST /api/v1/public/waitlist/join
     * Sign up for the PRO plan waitlist.
     *
     * Idempotent: if the email is already registered, returns 200 instead of 201
     * so that retry-happy front-end code or duplicate form submissions are handled
     * gracefully without surfacing an error to the user.
     *
     * Rate limit: 3 requests per minute per IP (enforced by RateLimitFilter).
     */
    @PostMapping("/waitlist/join")
    public ResponseEntity<Map<String, String>> joinWaitlist(
            @Valid @RequestBody WaitlistJoinRequest dto) {

        boolean created = proWaitlistService.join(dto);

        String message = "fr".equalsIgnoreCase(dto.language())
                ? "Vous êtes inscrit(e) sur la liste d'attente PRO. Nous vous contacterons au lancement."
                : "You're on the PRO waitlist. We'll reach out when we launch.";

        return ResponseEntity
                .status(created ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("message", message));
    }

    /**
     * GET /api/v1/public/testimonials?lang=fr
     * Returns all APPROVED testimonials for the public landing page, sorted by displayOrder.
     * The optional {@code lang} parameter filters by language ("fr" or "en").
     * No authentication required. Rate limit: covered by the /api/v1/public/** permit-all
     * path; no dedicated bucket needed for a read-only GET.
     */
    @GetMapping("/testimonials")
    public ResponseEntity<List<TestimonialResponse>> getApprovedTestimonials(
            @RequestParam(required = false) String lang) {
        return ResponseEntity.ok(testimonialService.getApprovedForDisplay(lang));
    }
}

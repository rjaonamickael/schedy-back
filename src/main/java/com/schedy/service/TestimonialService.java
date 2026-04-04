package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.TestimonialDto;
import com.schedy.dto.response.TestimonialResponse;
import com.schedy.entity.Testimonial;
import com.schedy.entity.Testimonial.TestimonialStatus;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.TestimonialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Testimonial lifecycle management.
 *
 * Roles:
 *   Org admin  — submit, view own submissions.
 *   Superadmin — view all, approve/reject, reorder.
 *   Public     — read APPROVED testimonials for landing page (no auth).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestimonialService {

    private final TestimonialRepository  testimonialRepository;
    private final OrganisationRepository organisationRepository;
    private final TenantContext          tenantContext;

    // =========================================================================
    // ORG ADMIN — submit
    // =========================================================================

    /**
     * An org admin submits a new testimonial.
     * The organisation ID is taken from TenantContext — the client cannot override it.
     * Status defaults to PENDING; a superadmin must approve before public display.
     *
     * Business rule: one PENDING testimonial per organisation at a time, to prevent
     * accidental spam from the UI.
     */
    @Transactional
    public TestimonialResponse submit(TestimonialDto dto) {
        String orgId = tenantContext.requireOrganisationId();

        // Guard: only one pending per org
        List<Testimonial> pending = testimonialRepository
                .findByOrganisationIdOrderByCreatedAtDesc(orgId)
                .stream()
                .filter(t -> t.getStatus() == TestimonialStatus.PENDING)
                .toList();

        if (!pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Un témoignage est déjà en attente de modération pour votre organisation. "
                + "Veuillez attendre la réponse avant d'en soumettre un nouveau.");
        }

        String orgName = organisationRepository.findById(orgId)
                .map(o -> o.getNom())
                .orElse(null);

        Testimonial entity = Testimonial.builder()
                .organisationId(orgId)
                .authorName(dto.authorName())
                .authorRole(dto.authorRole())
                .authorCity(dto.authorCity())
                .quote(dto.quote())
                .stars(dto.stars())
                .language(dto.language())
                .status(TestimonialStatus.PENDING)
                .displayOrder(0)
                .build();

        entity = testimonialRepository.save(entity);

        log.info("Testimonial submitted by org '{}' ({}): authorName='{}'",
                orgId, orgName, dto.authorName());

        return TestimonialResponse.from(entity, orgName);
    }

    // =========================================================================
    // ORG ADMIN — view own submissions
    // =========================================================================

    @Transactional(readOnly = true)
    public List<TestimonialResponse> getMyTestimonials() {
        String orgId = tenantContext.requireOrganisationId();

        String orgName = organisationRepository.findById(orgId)
                .map(o -> o.getNom())
                .orElse(null);

        return testimonialRepository
                .findByOrganisationIdOrderByCreatedAtDesc(orgId)
                .stream()
                .map(t -> TestimonialResponse.from(t, orgName))
                .toList();
    }

    // =========================================================================
    // PUBLIC — approved testimonials for landing page
    // =========================================================================

    /**
     * Returns APPROVED testimonials for public display, ordered by displayOrder.
     * If language is provided and non-blank, only testimonials in that language are returned.
     * Falls back to all approved testimonials when no language filter is given.
     */
    @Transactional(readOnly = true)
    public List<TestimonialResponse> getApprovedForDisplay(String language) {
        List<Testimonial> approved;

        if (language != null && !language.isBlank()) {
            approved = testimonialRepository
                    .findByStatusAndLanguageOrderByDisplayOrderAsc(TestimonialStatus.APPROVED, language);
        } else {
            approved = testimonialRepository
                    .findByStatusOrderByDisplayOrderAsc(TestimonialStatus.APPROVED);
        }

        // Public endpoint — never expose reviewedBy or organisationId data.
        // organisationName is safe to show and adds social proof.
        Set<String> orgIds = approved.stream()
                .map(Testimonial::getOrganisationId)
                .collect(Collectors.toSet());

        Map<String, String> orgNames = organisationRepository.findAllById(orgIds)
                .stream()
                .collect(Collectors.toMap(o -> o.getId(), o -> o.getNom()));

        return approved.stream()
                .map(t -> TestimonialResponse.from(t, orgNames.get(t.getOrganisationId())))
                .toList();
    }

    // =========================================================================
    // SUPERADMIN — full list
    // =========================================================================

    @Transactional(readOnly = true)
    public List<TestimonialResponse> getAllForAdmin() {
        List<Testimonial> all = testimonialRepository.findAllByOrderByCreatedAtDesc();

        Set<String> orgIds = all.stream()
                .map(Testimonial::getOrganisationId)
                .collect(Collectors.toSet());

        Map<String, String> orgNames = organisationRepository.findAllById(orgIds)
                .stream()
                .collect(Collectors.toMap(o -> o.getId(), o -> o.getNom()));

        return all.stream()
                .map(t -> TestimonialResponse.from(t, orgNames.get(t.getOrganisationId())))
                .toList();
    }

    // =========================================================================
    // SUPERADMIN — approve
    // =========================================================================

    /**
     * Approves a testimonial and sets its display order.
     * The superadmin email is taken from the security context.
     */
    @Transactional
    public TestimonialResponse approve(String id, int displayOrder) {
        Testimonial entity = requireTestimonial(id);

        if (entity.getStatus() == TestimonialStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ce témoignage est déjà approuvé.");
        }

        entity.setStatus(TestimonialStatus.APPROVED);
        entity.setDisplayOrder(displayOrder);
        entity.setReviewedAt(OffsetDateTime.now());
        entity.setReviewedBy(currentUserEmail());
        entity = testimonialRepository.save(entity);

        log.info("Testimonial {} approved by {} with displayOrder={}",
                id, entity.getReviewedBy(), displayOrder);

        String orgName = organisationRepository.findById(entity.getOrganisationId())
                .map(o -> o.getNom()).orElse(null);

        return TestimonialResponse.from(entity, orgName);
    }

    // =========================================================================
    // SUPERADMIN — reject
    // =========================================================================

    @Transactional
    public TestimonialResponse reject(String id) {
        Testimonial entity = requireTestimonial(id);

        if (entity.getStatus() == TestimonialStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ce témoignage est déjà rejeté.");
        }

        entity.setStatus(TestimonialStatus.REJECTED);
        entity.setReviewedAt(OffsetDateTime.now());
        entity.setReviewedBy(currentUserEmail());
        entity = testimonialRepository.save(entity);

        log.info("Testimonial {} rejected by {}", id, entity.getReviewedBy());

        String orgName = organisationRepository.findById(entity.getOrganisationId())
                .map(o -> o.getNom()).orElse(null);

        return TestimonialResponse.from(entity, orgName);
    }

    // =========================================================================
    // SUPERADMIN — update display order
    // =========================================================================

    @Transactional
    public TestimonialResponse updateDisplayOrder(String id, int order) {
        Testimonial entity = requireTestimonial(id);

        if (entity.getStatus() != TestimonialStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "L'ordre d'affichage ne peut être défini que sur les témoignages approuvés.");
        }

        entity.setDisplayOrder(order);
        entity = testimonialRepository.save(entity);

        log.info("Testimonial {} displayOrder updated to {} by {}",
                id, order, currentUserEmail());

        String orgName = organisationRepository.findById(entity.getOrganisationId())
                .map(o -> o.getNom()).orElse(null);

        return TestimonialResponse.from(entity, orgName);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Testimonial requireTestimonial(String id) {
        return testimonialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Témoignage introuvable : " + id));
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }
}

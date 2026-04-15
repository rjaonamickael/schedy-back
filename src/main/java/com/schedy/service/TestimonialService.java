package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.TestimonialDto;
import com.schedy.dto.response.TestimonialResponse;
import com.schedy.entity.Subscription;
import com.schedy.entity.Testimonial;
import com.schedy.entity.Testimonial.TestimonialStatus;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.SubscriptionRepository;
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
    private final SubscriptionRepository subscriptionRepository;
    private final TenantContext          tenantContext;
    private final R2StorageService       r2StorageService;

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

        // V44 — stamp the org's current plan tier on the testimonial row so
        // the card badge survives later plan changes. Null when the org has
        // no subscription record (edge case).
        Subscription.PlanTier planTier = subscriptionRepository
                .findByOrganisationId(orgId)
                .map(Subscription::getPlanTier)
                .orElse(null);

        // Security: a client cannot point logoUrl at an arbitrary host. The URL
        // must come from our own R2 upload endpoint (see R2StorageService#isOwnedUrl).
        String logoUrl = dto.logoUrl();
        if (logoUrl != null && !logoUrl.isBlank() && !r2StorageService.isOwnedUrl(logoUrl)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "L'URL du logo n'est pas valide. Téléversez le logo via le formulaire.");
        }

        Testimonial entity = Testimonial.builder()
                .organisationId(orgId)
                .authorName(trimmed(dto.authorName()))
                .authorRole(trimmed(dto.authorRole()))
                .authorCity(nullIfBlank(dto.authorCity()))
                .quote(trimmed(dto.quote()))
                .quoteTitle(nullIfBlank(dto.quoteTitle()))
                .stars(dto.stars())
                .language(dto.language())
                .linkedinUrl(nullIfBlank(dto.linkedinUrl()))
                .websiteUrl(nullIfBlank(dto.websiteUrl()))
                .logoUrl(nullIfBlank(logoUrl))
                .textProbleme(nullIfBlank(dto.textProbleme()))
                .textSolution(nullIfBlank(dto.textSolution()))
                .textImpact(nullIfBlank(dto.textImpact()))
                .facebookUrl(nullIfBlank(dto.facebookUrl()))
                .instagramUrl(nullIfBlank(dto.instagramUrl()))
                .twitterUrl(nullIfBlank(dto.twitterUrl()))
                .planTier(planTier)
                .status(TestimonialStatus.PENDING)
                .displayOrder(0)
                .build();

        entity = testimonialRepository.save(entity);

        log.info("Testimonial submitted by org '{}' ({}): authorName='{}' logoUrl={}",
                orgId, orgName, dto.authorName(), logoUrl != null);

        return TestimonialResponse.from(entity, orgName);
    }

    /**
     * Trims the input and returns {@code null} if the result is empty.
     * Guards against storing strings that are only whitespace or that carry
     * stray leading/trailing newlines from a textarea.
     */
    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Trims the input for required fields (non-null by Bean Validation).
     * Returns {@code null} only if the caller passed null — the @NotBlank
     * validator prevents that from reaching the service in practice.
     */
    private static String trimmed(String s) {
        return s == null ? null : s.trim();
    }

    // =========================================================================
    // ORG ADMIN — update own testimonial
    // =========================================================================

    /**
     * Updates an existing testimonial owned by the caller's organisation.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Only testimonials belonging to the caller's org can be edited —
     *       enforced by matching {@code tenantContext.requireOrganisationId()}
     *       against the row's {@code organisationId}. Mismatches return 404,
     *       not 403, to avoid leaking the existence of other orgs' rows.</li>
     *   <li>Editing an APPROVED or REJECTED testimonial resets its status to
     *       PENDING and clears review metadata — the superadmin must re-approve
     *       the modified content before it becomes public again.</li>
     *   <li>The {@code planTier} field is NOT touched: it was stamped at the
     *       original submission time and stays frozen as a historical record
     *       of what plan the org was on when they first wrote the testimonial.</li>
     *   <li>LogoUrl still goes through the R2-owned URL validation to prevent
     *       a caller from pointing logoUrl at an arbitrary host.</li>
     * </ul>
     */
    @Transactional
    public TestimonialResponse update(String id, TestimonialDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Testimonial entity = requireTestimonial(id);

        // Ownership check: a silent 404 avoids telling the caller "this id
        // exists but belongs to another tenant" (enumeration defense).
        if (!orgId.equals(entity.getOrganisationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Témoignage introuvable : " + id);
        }

        String logoUrl = dto.logoUrl();
        if (logoUrl != null && !logoUrl.isBlank() && !r2StorageService.isOwnedUrl(logoUrl)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "L'URL du logo n'est pas valide. Téléversez le logo via le formulaire.");
        }

        entity.setAuthorName(trimmed(dto.authorName()));
        entity.setAuthorRole(trimmed(dto.authorRole()));
        entity.setAuthorCity(nullIfBlank(dto.authorCity()));
        entity.setQuote(trimmed(dto.quote()));
        entity.setQuoteTitle(nullIfBlank(dto.quoteTitle()));
        entity.setStars(dto.stars());
        entity.setLanguage(dto.language());
        entity.setLinkedinUrl(nullIfBlank(dto.linkedinUrl()));
        entity.setWebsiteUrl(nullIfBlank(dto.websiteUrl()));
        entity.setLogoUrl(nullIfBlank(logoUrl));
        entity.setTextProbleme(nullIfBlank(dto.textProbleme()));
        entity.setTextSolution(nullIfBlank(dto.textSolution()));
        entity.setTextImpact(nullIfBlank(dto.textImpact()));
        entity.setFacebookUrl(nullIfBlank(dto.facebookUrl()));
        entity.setInstagramUrl(nullIfBlank(dto.instagramUrl()));
        entity.setTwitterUrl(nullIfBlank(dto.twitterUrl()));

        // Editing always kicks the row back to PENDING for re-moderation,
        // regardless of its previous status. Clear the review metadata so
        // the superadmin sees it as a fresh item in their queue.
        entity.setStatus(TestimonialStatus.PENDING);
        entity.setReviewedAt(null);
        entity.setReviewedBy(null);

        entity = testimonialRepository.save(entity);

        String orgName = organisationRepository.findById(orgId)
                .map(o -> o.getNom())
                .orElse(null);

        log.info("Testimonial {} updated by org '{}' — reset to PENDING", id, orgId);

        return TestimonialResponse.from(entity, orgName);
    }

    // =========================================================================
    // ORG ADMIN — delete own testimonial
    // =========================================================================

    /**
     * Deletes a testimonial owned by the caller's organisation. No superadmin
     * approval is required — an org is free to retract what it submitted.
     *
     * <p>Security mirrors {@link #update(String, TestimonialDto)}:
     * <ul>
     *   <li>Ownership is enforced via {@code tenantContext.requireOrganisationId()}
     *       against the row's {@code organisationId}. Mismatches return a silent
     *       404 (enumeration defense) rather than 403.</li>
     *   <li>The associated R2 logo, if any, is deleted best-effort after the
     *       row is removed — an R2 failure is logged but does not block the
     *       caller. Orphaned objects can be swept later if needed.</li>
     * </ul>
     */
    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Testimonial entity = requireTestimonial(id);

        if (!orgId.equals(entity.getOrganisationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Témoignage introuvable : " + id);
        }

        String logoUrl = entity.getLogoUrl();
        testimonialRepository.delete(entity);

        // Best-effort R2 cleanup — errors are logged inside deleteLogo(),
        // never rethrown, so the DB delete commit is authoritative.
        if (logoUrl != null && !logoUrl.isBlank()) {
            r2StorageService.deleteLogo(logoUrl);
        }

        log.info("Testimonial {} deleted by org '{}' (status was {})",
                id, orgId, entity.getStatus());
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

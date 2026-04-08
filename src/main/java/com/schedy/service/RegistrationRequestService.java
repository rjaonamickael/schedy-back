package com.schedy.service;

import com.schedy.dto.request.CreateOrgRequest;
import com.schedy.dto.request.RegistrationRequestDto;
import com.schedy.dto.response.OrgSummaryResponse;
import com.schedy.dto.response.RegistrationRequestResponse;
import com.schedy.entity.RegistrationRequest;
import com.schedy.entity.RegistrationRequest.RequestStatus;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.RegistrationRequestRepository;
import com.schedy.util.LocaleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for the public registration request flow.
 * Public submissions are unauthenticated; approve/reject operations require SUPERADMIN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationRequestService {

    private final RegistrationRequestRepository registrationRequestRepository;
    private final SuperAdminService             superAdminService;
    private final EmailService                  emailService;
    private final OrganisationRepository        organisationRepository;

    // =========================================================================
    // PUBLIC — submit a registration request
    // =========================================================================

    /**
     * Creates a new PENDING registration request.
     * Rejects duplicate PENDING requests from the same email to prevent spam.
     * Sends a confirmation email to the contact asynchronously.
     */
    @Transactional
    public RegistrationRequestResponse submitRequest(RegistrationRequestDto dto) {
        if (!Boolean.TRUE.equals(dto.certificationAccepted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La certification des informations est obligatoire.");
        }

        if (registrationRequestRepository.existsByContactEmailAndStatus(
                dto.contactEmail(), RequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Une demande est déjà en cours de traitement pour cet email. "
                + "Veuillez patienter ou contacter le support.");
        }

        if ("CAN".equals(dto.pays()) && (dto.province() == null || dto.province().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La province est obligatoire pour le Canada.");
        }

        RegistrationRequest entity = RegistrationRequest.builder()
                .organisationName(dto.organisationName())
                .contactName(dto.contactName())
                .contactEmail(dto.contactEmail())
                .contactPhone(dto.contactPhone())
                .pays(dto.pays())
                .province(dto.province())
                .adresse(dto.adresse())
                .businessNumber(dto.businessNumber())
                .provincialId(dto.provincialId())
                .nif(dto.nif())
                .stat(dto.stat())
                .desiredPlan(dto.desiredPlan())
                .employeeCount(dto.employeeCount())
                .message(dto.message())
                .billingCycle(dto.billingCycle())
                .certificationAccepted(Boolean.TRUE.equals(dto.certificationAccepted()))
                .certificationAcceptedAt(Boolean.TRUE.equals(dto.certificationAccepted()) ? OffsetDateTime.now() : null)
                .status(RequestStatus.PENDING)
                .build();

        entity = registrationRequestRepository.save(entity);

        // Confirmation email to organisation — best-effort, never blocks the response
        try {
            boolean isFrench = LocaleUtils.isFrenchSpeaking(dto.pays());
            emailService.sendRegistrationRequestConfirmation(
                    dto.contactEmail(), dto.contactName(), dto.organisationName(), isFrench);
        } catch (Exception e) {
            log.error("Failed to send registration confirmation email to {}: {}",
                    dto.contactEmail(), e.getMessage());
        }

        // Internal CRM notification to contact@schedy.work — best-effort
        try {
            emailService.sendRegistrationRequestInternalNotification(
                    dto.contactName(), dto.contactEmail(), dto.organisationName(),
                    dto.pays(), dto.province(), dto.desiredPlan(),
                    dto.employeeCount(), dto.billingCycle(), dto.message());
        } catch (Exception e) {
            log.error("Failed to send internal CRM notification for {}: {}",
                    dto.contactEmail(), e.getMessage());
        }

        log.info("New registration request from '{}' <{}> for organisation '{}'",
                dto.contactName(), dto.contactEmail(), dto.organisationName());

        return RegistrationRequestResponse.from(entity);
    }

    // =========================================================================
    // SUPERADMIN — list
    // =========================================================================

    @Transactional(readOnly = true)
    public List<RegistrationRequestResponse> findAll(String statusFilter) {
        List<RegistrationRequest> requests;

        if (statusFilter != null && !statusFilter.isBlank()) {
            RequestStatus status = parseStatus(statusFilter);
            requests = registrationRequestRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else {
            requests = registrationRequestRepository.findAllByOrderByCreatedAtDesc();
        }

        return requests.stream().map(RegistrationRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RegistrationRequestResponse findById(String id) {
        return RegistrationRequestResponse.from(requireRequest(id));
    }

    // =========================================================================
    // SUPERADMIN — approve
    // =========================================================================

    /**
     * Approves a registration request:
     * 1. Validates the request is still PENDING.
     * 2. Delegates to SuperAdminService.createOrganisation() — which creates the
     *    organisation, admin user, subscription, and sends the admin invitation email.
     * 3. Marks the request APPROVED.
     */
    @Transactional
    public RegistrationRequestResponse approve(String id, String superadminEmail, String planTier, String adminEmail) {
        RegistrationRequest request = requireRequest(id);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cette demande a déjà été traitée (statut : " + request.getStatus().name() + ").");
        }

        // MED-04: Idempotency guard — if an organisation with the same name was already
        // created (e.g. by a prior approve call that partially succeeded before a crash),
        // do not attempt a second creation. Mark the request APPROVED and return early
        // with a clear message so the operator knows the org already exists.
        if (organisationRepository.existsByNom(request.getOrganisationName())) {
            log.warn("approve(): organisation '{}' already exists — marking request {} as APPROVED without re-creating",
                    request.getOrganisationName(), id);
            request.setStatus(RequestStatus.APPROVED);
            request.setReviewedAt(OffsetDateTime.now());
            request.setReviewedBy(superadminEmail);
            registrationRequestRepository.save(request);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "L'organisation '" + request.getOrganisationName() + "' existe d\u00e9j\u00e0. "
                + "La demande a \u00e9t\u00e9 marqu\u00e9e APPROV\u00c9E sans recr\u00e9ation.");
        }

        // Use the explicit planTier from the caller if provided; fall back to the requested plan
        if (planTier == null || planTier.isBlank()) {
            planTier = resolvePlanTier(request.getDesiredPlan());
        }

        // Use the explicit adminEmail from the caller if provided; fall back to the contact email
        String resolvedAdminEmail = (adminEmail != null && !adminEmail.isBlank())
                ? adminEmail : request.getContactEmail();

        CreateOrgRequest createOrgRequest = new CreateOrgRequest(
                request.getOrganisationName(),
                resolvedAdminEmail,
                null,          // no password — the invitation email handles it
                request.getPays(),
                planTier
        );

        OrgSummaryResponse orgSummary;
        try {
            orgSummary = superAdminService.createOrganisation(createOrgRequest);
        } catch (ResponseStatusException rse) {
            // Re-wrap with context so the caller knows which request caused the conflict
            throw new ResponseStatusException(rse.getStatusCode(),
                "Impossible de créer l'organisation : " + rse.getReason());
        }

        // Copy identification data from registration request to the new organisation
        organisationRepository.findById(orgSummary.id()).ifPresent(org -> {
            org.setProvince(request.getProvince());
            org.setBusinessNumber(request.getBusinessNumber());
            org.setProvincialId(request.getProvincialId());
            org.setNif(request.getNif());
            org.setStat(request.getStat());
            org.setAdresse(request.getAdresse());
            organisationRepository.save(org);
        });

        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(superadminEmail);
        registrationRequestRepository.save(request);

        log.info("SuperAdmin {}: approved registration request '{}' → org id {}",
                superadminEmail, request.getOrganisationName(), orgSummary.id());

        return RegistrationRequestResponse.from(request);
    }

    // =========================================================================
    // SUPERADMIN — reject
    // =========================================================================

    /**
     * Rejects a registration request with a mandatory reason.
     * Sends a rejection email to the contact asynchronously.
     */
    @Transactional
    public RegistrationRequestResponse reject(String id, String reason, String superadminEmail) {
        RegistrationRequest request = requireRequest(id);

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cette demande a déjà été traitée (statut : " + request.getStatus().name() + ").");
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setRejectionReason(reason);
        request.setReviewedAt(OffsetDateTime.now());
        request.setReviewedBy(superadminEmail);
        registrationRequestRepository.save(request);

        // Rejection email — best-effort
        try {
            boolean isFrench = LocaleUtils.isFrenchSpeaking(request.getPays());
            emailService.sendRegistrationRequestRejection(
                    request.getContactEmail(),
                    request.getContactName(),
                    request.getOrganisationName(),
                    reason,
                    isFrench);
        } catch (Exception e) {
            log.error("Failed to send rejection email to {}: {}", request.getContactEmail(), e.getMessage());
        }

        log.info("SuperAdmin {}: rejected registration request '{}' — reason: {}",
                superadminEmail, request.getOrganisationName(), reason);

        return RegistrationRequestResponse.from(request);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private RegistrationRequest requireRequest(String id) {
        return registrationRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Demande d'inscription introuvable : " + id));
    }

    private RequestStatus parseStatus(String raw) {
        try {
            return RequestStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Statut invalide : '" + raw + "'. Valeurs acceptées : PENDING, APPROVED, REJECTED.");
        }
    }

    /**
     * Maps the desired plan string to a valid Subscription.PlanTier name.
     * "CUSTOM" requests are treated as PRO until a bespoke plan is agreed.
     */
    private String resolvePlanTier(String desiredPlan) {
        if (desiredPlan == null) return "ESSENTIALS";
        return switch (desiredPlan.toUpperCase()) {
            case "PRO", "STARTER" -> desiredPlan.toUpperCase();
            case "CUSTOM"         -> "PRO";
            default               -> "ESSENTIALS";
        };
    }

}

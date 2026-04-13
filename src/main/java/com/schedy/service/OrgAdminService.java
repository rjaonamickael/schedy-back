package com.schedy.service;

import com.schedy.dto.request.CreateOrgRequest;
import com.schedy.dto.request.UpdateOrgIdentificationsRequest;
import com.schedy.dto.response.OrgIdentificationsResponse;
import com.schedy.dto.response.OrgSummaryResponse;
import com.schedy.dto.response.SuperAdminDashboardResponse;
import com.schedy.entity.*;
import com.schedy.repository.*;
import com.schedy.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Organisation lifecycle management: CRUD, dashboard, identifications, cascade delete.
 * Does NOT inject TenantContext — operates cross-org by design.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgAdminService {

    private final OrganisationRepository        organisationRepository;
    private final UserRepository                userRepository;
    private final EmployeRepository             employeRepository;
    private final SubscriptionRepository        subscriptionRepository;
    private final PromoCodeRepository           promoCodeRepository;
    private final FeatureFlagRepository         featureFlagRepository;
    private final PlatformAnnouncementRepository announcementRepository;
    private final PointageRepository            pointageRepository;
    private final PointageCodeRepository        pointageCodeRepository;
    private final CreneauAssigneRepository      creneauAssigneRepository;
    private final DemandeCongeRepository        demandeCongeRepository;
    private final AbsenceImprevueRepository     absenceImprevueRepository;
    private final PauseRepository               pauseRepository;
    private final BanqueCongeRepository         banqueCongeRepository;
    private final ExigenceRepository            exigenceRepository;
    private final SiteRepository                siteRepository;
    private final RoleRepository                roleRepository;
    private final TypeCongeRepository           typeCongeRepository;
    private final JourFerieRepository           jourFerieRepository;
    private final ParametresRepository          parametresRepository;
    private final PasswordEncoder               passwordEncoder;
    private final EmailService                  emailService;

    @org.springframework.beans.factory.annotation.Value("${schedy.invitation.expiry-hours:24}")
    private int invitationExpiryHours;

    // ── DASHBOARD ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SuperAdminDashboardResponse getDashboard() {
        long totalOrgs       = organisationRepository.count();
        long activeOrgs      = organisationRepository.countByStatus(Organisation.STATUS_ACTIVE);
        long suspendedOrgs   = organisationRepository.countByStatus(Organisation.STATUS_SUSPENDED);
        long totalUsers      = userRepository.count();
        long totalEmployees  = employeRepository.count();

        Map<String, Long> orgsByPlan = subscriptionRepository.countByPlanTierGrouped()
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Subscription.PlanTier) row[0]).name(),
                        row -> (Long) row[1]
                ));

        Map<String, Long> orgsByStatus = subscriptionRepository.countByStatusGrouped()
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Subscription.SubscriptionStatus) row[0]).name(),
                        row -> (Long) row[1]
                ));

        return new SuperAdminDashboardResponse(
                totalOrgs, activeOrgs, suspendedOrgs,
                totalUsers, totalEmployees,
                orgsByPlan, orgsByStatus
        );
    }

    // ── ORGANISATIONS CRUD ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrgSummaryResponse> findAllOrganisations() {
        List<Organisation> orgs = organisationRepository.findAll();
        if (orgs.isEmpty()) return List.of();

        List<String> orgIds = orgs.stream().map(Organisation::getId).toList();

        Map<String, Long> employeeCounts = employeRepository
                .countGroupedByOrganisationId(orgIds).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        Map<String, Long> userCounts = userRepository
                .countGroupedByOrganisationId(orgIds).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        Map<String, Subscription> subscriptions = subscriptionRepository
                .findByOrganisationIdIn(orgIds).stream()
                .collect(Collectors.toMap(Subscription::getOrganisationId, s -> s, (a, b) -> a));

        Set<String> promoIds = subscriptions.values().stream()
                .map(Subscription::getPromoCodeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> promoCodeById = promoIds.isEmpty()
                ? Map.of()
                : promoCodeRepository.findByIdIn(promoIds).stream()
                        .collect(Collectors.toMap(PromoCode::getId, PromoCode::getCode));

        return orgs.stream().map(org -> {
            String orgId = org.getId();
            long empCount = employeeCounts.getOrDefault(orgId, 0L);
            long usrCount = userCounts.getOrDefault(orgId, 0L);
            Subscription sub = subscriptions.get(orgId);
            String planTier  = sub != null ? sub.getPlanTier().name() : "NONE";
            String promoCode = (sub != null && sub.getPromoCodeId() != null)
                    ? promoCodeById.get(sub.getPromoCodeId()) : null;
            return new OrgSummaryResponse(
                    org.getId(), org.getNom(), org.getStatus(),
                    planTier, org.getPays(), empCount, usrCount, org.getCreatedAt(), promoCode);
        }).toList();
    }

    @Transactional(readOnly = true)
    public OrgSummaryResponse findOrganisation(String id) {
        return toOrgSummary(requireOrg(id));
    }

    @Transactional
    public OrgSummaryResponse createOrganisation(CreateOrgRequest request) {
        if (organisationRepository.existsByNom(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une organisation avec ce nom existe d\u00e9j\u00e0 : " + request.name());
        }

        Organisation org = Organisation.builder()
                .nom(request.name())
                .status("ACTIVE")
                .createdAt(OffsetDateTime.now())
                .pays(request.pays())
                .build();
        org = organisationRepository.save(org);

        if (userRepository.existsByEmail(request.adminEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un utilisateur avec cet email existe d\u00e9j\u00e0 : " + request.adminEmail());
        }
        String rawToken = CryptoUtil.generateSecureToken();
        String hashedToken = CryptoUtil.sha256(rawToken);
        User admin = User.builder()
                .email(request.adminEmail())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(User.UserRole.ADMIN)
                .organisationId(org.getId())
                .invitationToken(hashedToken)
                .invitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)))
                .build();
        userRepository.save(admin);

        try {
            emailService.sendAdminInvitationEmail(request.adminEmail(), org.getNom(), rawToken);
        } catch (Exception e) {
            log.error("Failed to send admin invitation email to {}: {}", request.adminEmail(), e.getMessage());
        }

        Subscription.PlanTier tier = Subscription.PlanTier.ESSENTIALS;
        if (request.planTier() != null && !request.planTier().isBlank()) {
            try { tier = Subscription.PlanTier.valueOf(request.planTier().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        Subscription sub = Subscription.builder()
                .organisationId(org.getId())
                .planTier(tier)
                .status(Subscription.SubscriptionStatus.TRIAL)
                .trialEndsAt(OffsetDateTime.now().plusDays(30))
                .maxEmployees(15)
                .maxSites(1)
                .build();
        subscriptionRepository.save(sub);

        log.info("SuperAdmin: created organisation '{}' with admin '{}'", org.getNom(), request.adminEmail());
        return toOrgSummary(org);
    }

    @Transactional
    public void deleteOrganisation(String id) {
        Organisation org = requireOrg(id);
        String orgId = org.getId();

        // Cascade delete in dependency order (leaf -> root)
        pointageRepository.deleteByOrganisationId(orgId);
        pointageCodeRepository.deleteByOrganisationId(orgId);
        creneauAssigneRepository.deleteByOrganisationId(orgId);
        demandeCongeRepository.deleteByOrganisationId(orgId);
        banqueCongeRepository.deleteByOrganisationId(orgId);
        absenceImprevueRepository.deleteByOrganisationId(orgId);
        pauseRepository.deleteByOrganisationId(orgId);
        exigenceRepository.deleteByOrganisationId(orgId);
        employeRepository.deleteByOrganisationId(orgId);
        siteRepository.deleteByOrganisationId(orgId);
        roleRepository.deleteByOrganisationId(orgId);
        typeCongeRepository.deleteByOrganisationId(orgId);
        jourFerieRepository.deleteByOrganisationId(orgId);
        parametresRepository.deleteByOrganisationId(orgId);
        userRepository.deleteByOrganisationId(orgId);
        featureFlagRepository.deleteByOrganisationId(orgId);
        subscriptionRepository.deleteByOrganisationId(orgId);
        announcementRepository.deleteByOrganisationId(orgId);
        // Keep impersonation logs for audit trail
        organisationRepository.delete(org);

        log.warn("SuperAdmin: DELETED organisation '{}' (id: {})", org.getNom(), orgId);
    }

    private static final Set<String> VALID_ORG_STATUSES = Set.of(
            Organisation.STATUS_ACTIVE, Organisation.STATUS_SUSPENDED);

    @Transactional
    public OrgSummaryResponse updateOrgStatus(String id, String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le statut ne peut pas \u00eatre vide.");
        }
        String normalizedStatus = status.toUpperCase().trim();
        if (!VALID_ORG_STATUSES.contains(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Statut invalide : '" + status + "'. Valeurs accept\u00e9es : " + String.join(", ", VALID_ORG_STATUSES));
        }
        Organisation org = requireOrg(id);
        org.setStatus(normalizedStatus);
        organisationRepository.save(org);
        log.info("SuperAdmin: organisation '{}' status updated to '{}'", org.getNom(), normalizedStatus);
        return toOrgSummary(org);
    }

    @Transactional
    public OrgSummaryResponse updateOrgPays(String id, String pays) {
        Organisation org = requireOrg(id);
        org.setPays(pays);
        organisationRepository.save(org);
        log.info("SuperAdmin: organisation '{}' pays updated to '{}'", org.getNom(), pays);
        return toOrgSummary(org);
    }

    // ── IDENTIFICATIONS ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrgIdentificationsResponse getOrgIdentifications(String orgId) {
        Organisation org = requireOrg(orgId);
        return toIdentificationsResponse(org);
    }

    @Transactional
    public OrgIdentificationsResponse updateOrgIdentifications(String orgId, UpdateOrgIdentificationsRequest request) {
        Organisation org = requireOrg(orgId);
        org.setProvince(request.province());
        org.setBusinessNumber(request.businessNumber());
        org.setProvincialId(request.provincialId());
        org.setNif(request.nif());
        org.setStat(request.stat());
        organisationRepository.save(org);
        log.info("SuperAdmin: organisation '{}' identifications updated", org.getNom());
        return toIdentificationsResponse(org);
    }

    @Transactional
    public OrgIdentificationsResponse updateOrgVerificationStatus(
            String orgId, String status, String note, String superadminEmail) {
        Organisation org = requireOrg(orgId);
        org.setVerificationStatus(status);
        org.setVerifiedBy(superadminEmail);
        org.setVerifiedAt(OffsetDateTime.now());
        org.setVerificationNote(note);
        organisationRepository.save(org);
        log.info("SuperAdmin {}: organisation '{}' verification status set to '{}'",
                superadminEmail, org.getNom(), status);
        return toIdentificationsResponse(org);
    }

    // ── RESEND INVITATION ───────────────────────────────────────────────────

    @Transactional
    public void resendAdminInvitation(String orgId) {
        Organisation org = requireOrg(orgId);
        User admin = userRepository.findFirstByOrganisationIdAndRole(orgId, User.UserRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun administrateur trouv\u00e9 pour cette organisation"));

        String rawToken = CryptoUtil.generateSecureToken();
        String hashedToken = CryptoUtil.sha256(rawToken);
        admin.setInvitationToken(hashedToken);
        admin.setInvitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)));
        admin.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        admin.setPasswordSet(false);
        userRepository.save(admin);

        try {
            emailService.sendAdminInvitationEmail(admin.getEmail(), org.getNom(), rawToken);
            log.info("Admin invitation resent for org '{}' to {}", org.getNom(), admin.getEmail());
        } catch (Exception e) {
            log.error("Failed to send admin invitation email to {} for org '{}': {}",
                    admin.getEmail(), org.getNom(), e.getMessage());
        }
    }

    // ── SHARED HELPERS (public for injection by CommercialAdminService / PlatformAdminService) ──

    public Organisation requireOrg(String id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Organisation introuvable : " + id));
    }

    /**
     * Builds an {@link OrgSummaryResponse} for a <strong>single</strong> organisation by
     * issuing up to 4 targeted queries (employees count, users count, subscription, promo code).
     *
     * <p><strong>Acceptable for single-entity lookup.</strong> Each call site ({@code findOrganisation},
     * {@code createOrganisation}, {@code updateOrgStatus}, {@code updateOrgPays}) operates on
     * exactly one {@code Organisation} instance, so the fixed overhead of 3-4 queries is
     * proportional and predictable regardless of the total number of organisations in the database.
     *
     * <p><strong>DO NOT call this method inside a loop or a stream over multiple organisations.</strong>
     * For bulk/list operations use {@link #findAllOrganisations()}, which issues exactly 5 batch
     * queries for N organisations via {@code countGroupedByOrganisationId},
     * {@code findByOrganisationIdIn}, and {@code findByIdIn}.
     *
     * @param org the already-loaded {@link Organisation} entity (must not be {@code null})
     * @return a fully populated summary response
     */
    private OrgSummaryResponse toOrgSummary(Organisation org) {
        long employeeCount = employeRepository.countByOrganisationId(org.getId());
        long userCount = userRepository.countByOrganisationId(org.getId());
        var sub = subscriptionRepository.findByOrganisationId(org.getId()).orElse(null);
        String promoCode = (sub != null && sub.getPromoCodeId() != null)
                ? promoCodeRepository.findById(sub.getPromoCodeId()).map(PromoCode::getCode).orElse(null)
                : null;
        String planTier = sub != null ? sub.getPlanTier().name() : "NONE";
        return new OrgSummaryResponse(
                org.getId(), org.getNom(), org.getStatus(),
                planTier, org.getPays(), employeeCount, userCount, org.getCreatedAt(), promoCode);
    }

    private OrgIdentificationsResponse toIdentificationsResponse(Organisation org) {
        return new OrgIdentificationsResponse(
            org.getId(), org.getNom(), org.getPays(),
            org.getProvince(), org.getBusinessNumber(), org.getProvincialId(),
            org.getNif(), org.getStat(),
            org.getVerificationStatus(), org.getVerifiedBy(),
            org.getVerifiedAt(), org.getVerificationNote());
    }
}

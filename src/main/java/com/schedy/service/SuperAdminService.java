package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.*;
import com.schedy.dto.response.ImpersonateResponse;
import com.schedy.dto.response.OrgSummaryResponse;
import com.schedy.dto.response.SuperAdminDashboardResponse;
import com.schedy.entity.*;
import com.schedy.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Platform-level administration service.
 * This service intentionally does NOT inject TenantContext — it operates across
 * all organisations and must never be scoped to a single tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final OrganisationRepository        organisationRepository;
    private final UserRepository                userRepository;
    private final EmployeRepository             employeRepository;
    private final SubscriptionRepository        subscriptionRepository;
    private final PromoCodeRepository           promoCodeRepository;
    private final FeatureFlagRepository         featureFlagRepository;
    private final PlatformAnnouncementRepository announcementRepository;
    private final ImpersonationLogRepository    impersonationLogRepository;
    private final PointageRepository            pointageRepository;
    private final PointageCodeRepository        pointageCodeRepository;
    private final CreneauAssigneRepository      creneauAssigneRepository;
    private final DemandeCongeRepository        demandeCongeRepository;
    private final BanqueCongeRepository         banqueCongeRepository;
    private final ExigenceRepository            exigenceRepository;
    private final SiteRepository                siteRepository;
    private final RoleRepository                roleRepository;
    private final TypeCongeRepository           typeCongeRepository;
    private final JourFerieRepository           jourFerieRepository;
    private final ParametresRepository          parametresRepository;
    private final PasswordEncoder               passwordEncoder;
    private final JwtUtil                       jwtUtil;
    private final EmailService                  emailService;

    @org.springframework.beans.factory.annotation.Value("${schedy.invitation.expiry-hours:24}")
    private int invitationExpiryHours;

    // =========================================================================
    // DASHBOARD
    // =========================================================================

    @Transactional(readOnly = true)
    public SuperAdminDashboardResponse getDashboard() {
        long totalOrgs       = organisationRepository.count();
        long activeOrgs      = organisationRepository.countByStatus("ACTIVE");
        long suspendedOrgs   = organisationRepository.countByStatus("SUSPENDED");
        long totalUsers      = userRepository.count();
        long totalEmployees  = employeRepository.count();

        Map<String, Long> orgsByPlan = Arrays.stream(Subscription.PlanTier.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        tier -> (long) subscriptionRepository.findByPlanTier(tier).size()
                ));

        Map<String, Long> orgsByStatus = Arrays.stream(Subscription.SubscriptionStatus.values())
                .collect(Collectors.toMap(
                        Enum::name,
                        status -> (long) subscriptionRepository.findByStatus(status).size()
                ));

        return new SuperAdminDashboardResponse(
                totalOrgs, activeOrgs, suspendedOrgs,
                totalUsers, totalEmployees,
                orgsByPlan, orgsByStatus
        );
    }

    // =========================================================================
    // ORGANISATIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<OrgSummaryResponse> findAllOrganisations() {
        return organisationRepository.findAll().stream()
                .map(this::toOrgSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrgSummaryResponse findOrganisation(String id) {
        Organisation org = requireOrg(id);
        return toOrgSummary(org);
    }

    @Transactional
    public OrgSummaryResponse createOrganisation(CreateOrgRequest request) {
        if (organisationRepository.existsByNom(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une organisation avec ce nom existe déjà : " + request.name());
        }

        Organisation org = Organisation.builder()
                .nom(request.name())
                .status("ACTIVE")
                .createdAt(OffsetDateTime.now())
                .pays(request.pays())
                .build();
        org = organisationRepository.save(org);

        // Create the first ADMIN user for this organisation
        if (userRepository.existsByEmail(request.adminEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un utilisateur avec cet email existe d\u00e9j\u00e0 : " + request.adminEmail());
        }
        String rawToken = generateSecureToken();
        String hashedToken = com.schedy.util.CryptoUtil.sha256(rawToken);
        User admin = User.builder()
                .email(request.adminEmail())
                .password(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .role(User.UserRole.ADMIN)
                .organisationId(org.getId())
                .invitationToken(hashedToken)
                .invitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)))
                .build();
        userRepository.save(admin);

        // Send admin invitation email (best-effort)
        try {
            boolean isFrench = resolveIsFrench(org);
            emailService.sendAdminInvitationEmail(request.adminEmail(), org.getNom(), rawToken, isFrench);
        } catch (Exception e) {
            log.error("Failed to send admin invitation email to {}: {}", request.adminEmail(), e.getMessage());
        }

        Subscription.PlanTier tier = Subscription.PlanTier.FREE;
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

        // Cascade delete in dependency order
        pointageRepository.deleteByOrganisationId(orgId);
        pointageCodeRepository.deleteByOrganisationId(orgId);
        creneauAssigneRepository.deleteByOrganisationId(orgId);
        demandeCongeRepository.deleteByOrganisationId(orgId);
        banqueCongeRepository.deleteByOrganisationId(orgId);
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

    @Transactional
    public OrgSummaryResponse updateOrgStatus(String id, String status) {
        Organisation org = requireOrg(id);
        org.setStatus(status.toUpperCase());
        organisationRepository.save(org);
        log.info("SuperAdmin: organisation '{}' status updated to '{}'", org.getNom(), status);
        return toOrgSummary(org);
    }

    // =========================================================================
    // SUBSCRIPTIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public Subscription getSubscription(String orgId) {
        requireOrg(orgId);
        return subscriptionRepository.findByOrganisationId(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun abonnement trouvé pour l'organisation : " + orgId));
    }

    @Transactional
    public Subscription updateSubscription(String orgId, SubscriptionDto dto) {
        Subscription sub = getSubscription(orgId);
        if (dto.planTier() != null) {
            sub.setPlanTier(Subscription.PlanTier.valueOf(dto.planTier().toUpperCase()));
        }
        sub.setMaxEmployees(dto.maxEmployees() > 0 ? dto.maxEmployees() : sub.getMaxEmployees());
        sub.setMaxSites(dto.maxSites() > 0 ? dto.maxSites() : sub.getMaxSites());
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public Subscription applyPromoCode(String orgId, String promoCodeStr) {
        PromoCode promo = validatePromoCode(promoCodeStr);
        Subscription sub = getSubscription(orgId);

        sub.setPromoCodeId(promo.getId());
        if (promo.getPlanOverride() != null) {
            sub.setPlanTier(Subscription.PlanTier.valueOf(promo.getPlanOverride().toUpperCase()));
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        }

        promo.setCurrentUses(promo.getCurrentUses() + 1);
        promoCodeRepository.save(promo);

        log.info("SuperAdmin: promo '{}' applied to org '{}'", promoCodeStr, orgId);
        return subscriptionRepository.save(sub);
    }

    // =========================================================================
    // PROMO CODES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<PromoCode> findAllPromoCodes() {
        return promoCodeRepository.findAll();
    }

    @Transactional
    public PromoCode createPromoCode(PromoCodeDto dto) {
        if (promoCodeRepository.existsByCode(dto.code().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un code promo avec ce code existe déjà : " + dto.code());
        }
        PromoCode promo = PromoCode.builder()
                .code(dto.code().toUpperCase())
                .description(dto.description())
                .discountPercent(dto.discountPercent())
                .discountMonths(dto.discountMonths())
                .planOverride(dto.planOverride() != null ? dto.planOverride().toUpperCase() : null)
                .maxUses(dto.maxUses())
                .validFrom(dto.validFrom() != null ? dto.validFrom() : OffsetDateTime.now())
                .validTo(dto.validTo())
                .active(true)
                .build();
        return promoCodeRepository.save(promo);
    }

    @Transactional
    public PromoCode updatePromoCode(String id, PromoCodeDto dto) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Code promo introuvable : " + id));
        promo.setDescription(dto.description());
        promo.setDiscountPercent(dto.discountPercent());
        promo.setDiscountMonths(dto.discountMonths());
        promo.setPlanOverride(dto.planOverride() != null ? dto.planOverride().toUpperCase() : null);
        promo.setMaxUses(dto.maxUses());
        promo.setValidTo(dto.validTo());
        return promoCodeRepository.save(promo);
    }

    @Transactional
    public void deletePromoCode(String id) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Code promo introuvable : " + id));
        promo.setActive(false); // Soft delete
        promoCodeRepository.save(promo);
        log.info("SuperAdmin: promo code '{}' deactivated", promo.getCode());
    }

    /**
     * Validates a promo code: exists, active, within date range, not exhausted.
     */
    @Transactional(readOnly = true)
    public PromoCode validatePromoCode(String code) {
        PromoCode promo = promoCodeRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Code promo invalide : " + code));

        if (!promo.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce code promo est désactivé.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce code promo n'est pas encore valide.");
        }
        if (promo.getValidTo() != null && now.isAfter(promo.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce code promo a expiré.");
        }
        if (promo.getMaxUses() != null && promo.getCurrentUses() >= promo.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce code promo a atteint son nombre maximal d'utilisations.");
        }

        return promo;
    }

    // =========================================================================
    // FEATURE FLAGS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<FeatureFlag> getFeatureFlags(String orgId) {
        requireOrg(orgId);
        return featureFlagRepository.findByOrganisationId(orgId);
    }

    @Transactional
    public List<FeatureFlag> updateFeatureFlags(String orgId, List<FeatureFlagDto> dtos) {
        requireOrg(orgId);
        List<FeatureFlag> result = new ArrayList<>();
        for (FeatureFlagDto dto : dtos) {
            FeatureFlag flag = featureFlagRepository
                    .findByOrganisationIdAndFeatureKey(orgId, dto.featureKey())
                    .orElseGet(() -> FeatureFlag.builder()
                            .organisationId(orgId)
                            .featureKey(dto.featureKey())
                            .build());
            flag.setEnabled(dto.enabled());
            result.add(featureFlagRepository.save(flag));
        }
        return result;
    }

    // =========================================================================
    // ANNOUNCEMENTS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<PlatformAnnouncement> getAnnouncements() {
        return announcementRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional
    public PlatformAnnouncement createAnnouncement(AnnouncementDto dto) {
        PlatformAnnouncement.Severity severity = parseSeverity(dto.severity());
        PlatformAnnouncement announcement = PlatformAnnouncement.builder()
                .title(dto.title())
                .body(dto.body())
                .severity(severity)
                .active(dto.active())
                .expiresAt(parseExpiresAt(dto.expiresAt()))
                .build();
        return announcementRepository.save(announcement);
    }

    @Transactional
    public PlatformAnnouncement updateAnnouncement(String id, AnnouncementDto dto) {
        PlatformAnnouncement ann = announcementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Annonce introuvable : " + id));
        ann.setTitle(dto.title());
        ann.setBody(dto.body());
        ann.setSeverity(parseSeverity(dto.severity()));
        ann.setActive(dto.active());
        ann.setExpiresAt(parseExpiresAt(dto.expiresAt()));
        return announcementRepository.save(ann);
    }

    /** Parse an expiration date string — accepts both ISO datetime and plain date (YYYY-MM-DD). */
    private java.time.OffsetDateTime parseExpiresAt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return java.time.OffsetDateTime.parse(raw);
        } catch (java.time.format.DateTimeParseException e) {
            try {
                // Plain date → end of day UTC
                return java.time.LocalDate.parse(raw)
                        .atTime(23, 59, 59)
                        .atOffset(java.time.ZoneOffset.UTC);
            } catch (java.time.format.DateTimeParseException e2) {
                return null;
            }
        }
    }

    @Transactional
    public void deleteAnnouncement(String id) {
        if (!announcementRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annonce introuvable : " + id);
        }
        announcementRepository.deleteById(id);
    }

    // =========================================================================
    // IMPERSONATION
    // =========================================================================

    @Transactional
    public ImpersonateResponse generateImpersonationToken(
            String orgId, String superadminEmail, String reason, String ipAddress) {

        Organisation org = requireOrg(orgId);

        // Find any ADMIN user in the target organisation to use as JWT subject
        User targetAdmin = userRepository.findFirstByOrganisationIdAndRole(orgId, User.UserRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun administrateur trouvé pour l'organisation : " + orgId));

        String token = jwtUtil.generateImpersonationToken(
                targetAdmin.getEmail(), orgId, superadminEmail);

        ImpersonationLog logEntry = ImpersonationLog.builder()
                .superadminEmail(superadminEmail)
                .targetOrgId(orgId)
                .targetOrgName(org.getNom())
                .ipAddress(ipAddress)
                .reason(reason)
                .build();
        impersonationLogRepository.save(logEntry);

        log.warn("IMPERSONATION: {} impersonated org '{}' ({})", superadminEmail, org.getNom(), orgId);

        // 30 minutes expiry in seconds
        return new ImpersonateResponse(token, org.getNom(), 30 * 60L);
    }

    @Transactional(readOnly = true)
    public Page<ImpersonationLog> getImpersonationLog(int page, int size) {
        return impersonationLogRepository.findAllByOrderByStartedAtDesc(
                PageRequest.of(page, size));
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Organisation requireOrg(String id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Organisation introuvable : " + id));
    }

    private OrgSummaryResponse toOrgSummary(Organisation org) {
        long employeeCount = employeRepository.countByOrganisationId(org.getId());
        long userCount = userRepository.countByOrganisationId(org.getId());

        String promoCode = subscriptionRepository.findByOrganisationId(org.getId())
                .flatMap(sub -> sub.getPromoCodeId() != null
                        ? promoCodeRepository.findById(sub.getPromoCodeId()).map(PromoCode::getCode)
                        : Optional.empty())
                .orElse(null);

        String planTier = subscriptionRepository.findByOrganisationId(org.getId())
                .map(sub -> sub.getPlanTier().name())
                .orElse("NONE");

        return new OrgSummaryResponse(
                org.getId(),
                org.getNom(),
                org.getStatus(),
                planTier,
                employeeCount,
                userCount,
                org.getCreatedAt(),
                promoCode
        );
    }

    private PlatformAnnouncement.Severity parseSeverity(String severity) {
        if (severity == null) return PlatformAnnouncement.Severity.INFO;
        try {
            return PlatformAnnouncement.Severity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PlatformAnnouncement.Severity.INFO;
        }
    }

    @Transactional
    public void resendAdminInvitation(String orgId) {
        Organisation org = requireOrg(orgId);
        User admin = userRepository.findFirstByOrganisationIdAndRole(orgId, User.UserRole.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun administrateur trouvé pour cette organisation"));

        String rawToken = generateSecureToken();
        String hashedToken = com.schedy.util.CryptoUtil.sha256(rawToken);
        admin.setInvitationToken(hashedToken);
        admin.setInvitationTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(invitationExpiryHours)));
        admin.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        admin.setPasswordSet(false);
        userRepository.save(admin);

        boolean isFrench = resolveIsFrench(org);
        try {
            emailService.sendAdminInvitationEmail(admin.getEmail(), org.getNom(), rawToken, isFrench);
            log.info("Admin invitation resent for org '{}' to {}", org.getNom(), admin.getEmail());
        } catch (Exception e) {
            log.error("Failed to send admin invitation email to {} for org '{}': {}",
                    admin.getEmail(), org.getNom(), e.getMessage());
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private boolean resolveIsFrench(Organisation org) {
        String pays = org.getPays();
        if (pays == null) return false;
        String p = pays.toUpperCase();
        return p.startsWith("FR") || "MDG".equals(p) || "MG".equals(p)
                || "BE".equals(p) || "CH".equals(p) || "CA".equals(p) || "CAN".equals(p);
    }
}

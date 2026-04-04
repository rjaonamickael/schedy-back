package com.schedy.service;

import com.schedy.config.JwtUtil;
import com.schedy.dto.request.*;
import com.schedy.dto.response.AnnouncementResponse;
import com.schedy.dto.response.FeatureFlagResponse;
import com.schedy.dto.response.ImpersonateResponse;
import com.schedy.dto.response.ImpersonationLogResponse;
import com.schedy.dto.response.OrgSummaryResponse;
import com.schedy.dto.response.PlanTemplateResponse;
import com.schedy.dto.response.PromoCodeResponse;
import com.schedy.dto.response.SubscriptionResponse;
import com.schedy.dto.response.SuperAdminDashboardResponse;
import com.schedy.entity.*;
import com.schedy.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.schedy.util.CryptoUtil;
import com.schedy.util.LocaleUtils;
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
    private final AbsenceImprevueRepository     absenceImprevueRepository;
    private final PauseRepository               pauseRepository;
    private final BanqueCongeRepository         banqueCongeRepository;
    private final ExigenceRepository            exigenceRepository;
    private final SiteRepository                siteRepository;
    private final RoleRepository                roleRepository;
    private final TypeCongeRepository           typeCongeRepository;
    private final JourFerieRepository           jourFerieRepository;
    private final ParametresRepository          parametresRepository;
    private final PlanTemplateRepository        planTemplateRepository;
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

    // =========================================================================
    // ORGANISATIONS
    // =========================================================================

    /**
     * Returns a summary for every organisation using exactly 5 queries regardless of
     * how many organisations exist (1 per table: organisation, employe, app_user,
     * subscription, promo_code) — down from 4N+1 in the previous loop-based approach.
     */
    @Transactional(readOnly = true)
    public List<OrgSummaryResponse> findAllOrganisations() {
        List<Organisation> orgs = organisationRepository.findAll();
        if (orgs.isEmpty()) {
            return List.of();
        }

        List<String> orgIds = orgs.stream().map(Organisation::getId).toList();

        // Query 2 — employee counts grouped by org
        Map<String, Long> employeeCounts = employeRepository
                .countGroupedByOrganisationId(orgIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long)   row[1]
                ));

        // Query 3 — user counts grouped by org
        Map<String, Long> userCounts = userRepository
                .countGroupedByOrganisationId(orgIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long)   row[1]
                ));

        // Query 4 — all subscriptions for these orgs; keep first if somehow duplicated
        Map<String, Subscription> subscriptions = subscriptionRepository
                .findByOrganisationIdIn(orgIds)
                .stream()
                .collect(Collectors.toMap(
                        Subscription::getOrganisationId,
                        s -> s,
                        (existing, duplicate) -> existing
                ));

        // Query 5 — batch-load only the promo codes that are actually referenced
        Set<String> promoIds = subscriptions.values().stream()
                .map(Subscription::getPromoCodeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, String> promoCodeById = promoIds.isEmpty()
                ? Map.of()
                : promoCodeRepository.findByIdIn(promoIds)
                        .stream()
                        .collect(Collectors.toMap(PromoCode::getId, PromoCode::getCode));

        // Assemble summaries with no further DB access
        return orgs.stream().map(org -> {
            String orgId = org.getId();
            long empCount = employeeCounts.getOrDefault(orgId, 0L);
            long usrCount = userCounts.getOrDefault(orgId, 0L);
            Subscription sub = subscriptions.get(orgId);

            String planTier  = sub != null ? sub.getPlanTier().name() : "NONE";
            String promoCode = (sub != null && sub.getPromoCodeId() != null)
                    ? promoCodeById.get(sub.getPromoCodeId())
                    : null;

            return new OrgSummaryResponse(
                    org.getId(), org.getNom(), org.getStatus(),
                    planTier, org.getPays(), empCount, usrCount, org.getCreatedAt(), promoCode
            );
        }).toList();
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
        String rawToken = CryptoUtil.generateSecureToken();
        String hashedToken = CryptoUtil.sha256(rawToken);
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

        // Cascade delete in dependency order (leaf → root)
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

    @Transactional
    public OrgSummaryResponse updateOrgStatus(String id, String status) {
        Organisation org = requireOrg(id);
        org.setStatus(status.toUpperCase());
        organisationRepository.save(org);
        log.info("SuperAdmin: organisation '{}' status updated to '{}'", org.getNom(), status);
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

    // =========================================================================
    // SUBSCRIPTIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(String orgId) {
        requireOrg(orgId);
        Subscription sub = subscriptionRepository.findByOrganisationId(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun abonnement trouvé pour l'organisation : " + orgId));
        return SubscriptionResponse.from(sub);
    }

    /**
     * Internal helper that returns the raw entity — used by other service methods
     * that need to mutate the Subscription before saving.
     */
    private Subscription requireSubscription(String orgId) {
        requireOrg(orgId);
        return subscriptionRepository.findByOrganisationId(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun abonnement trouvé pour l'organisation : " + orgId));
    }

    @Transactional
    public SubscriptionResponse updateSubscription(String orgId, SubscriptionDto dto) {
        Subscription sub = requireSubscription(orgId);
        if (dto.planTier() != null) {
            sub.setPlanTier(Subscription.PlanTier.valueOf(dto.planTier().toUpperCase()));
        }
        if (dto.maxEmployees() != null && dto.maxEmployees() > 0) sub.setMaxEmployees(dto.maxEmployees());
        if (dto.maxSites() != null && dto.maxSites() > 0) sub.setMaxSites(dto.maxSites());
        if (dto.trialEndsAt() != null) {
            sub.setTrialEndsAt(dto.trialEndsAt());
        }
        return SubscriptionResponse.from(subscriptionRepository.save(sub));
    }

    @Transactional
    public SubscriptionResponse applyPromoCode(String orgId, String promoCodeStr) {
        PromoCode promo = validatePromoCode(promoCodeStr);
        Subscription sub = requireSubscription(orgId);

        sub.setPromoCodeId(promo.getId());
        if (promo.getPlanOverride() != null) {
            sub.setPlanTier(Subscription.PlanTier.valueOf(promo.getPlanOverride().toUpperCase()));
            sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        }

        promo.setCurrentUses(promo.getCurrentUses() + 1);
        promoCodeRepository.save(promo);

        log.info("SuperAdmin: promo '{}' applied to org '{}'", promoCodeStr, orgId);
        return SubscriptionResponse.from(subscriptionRepository.save(sub));
    }

    // =========================================================================
    // PROMO CODES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> findAllPromoCodes() {
        return promoCodeRepository.findAll().stream()
                .map(PromoCodeResponse::from)
                .toList();
    }

    @Transactional
    public PromoCodeResponse createPromoCode(PromoCodeDto dto) {
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
                .active(dto.active() != null ? dto.active() : true)
                .build();
        return PromoCodeResponse.from(promoCodeRepository.save(promo));
    }

    @Transactional
    public PromoCodeResponse updatePromoCode(String id, PromoCodeDto dto) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Code promo introuvable : " + id));
        // Reject updates on soft-deleted codes unless explicitly reactivating
        if (!promo.isActive() && (dto.active() == null || !dto.active())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce code promo est désactivé. Envoyez active=true pour le réactiver.");
        }
        promo.setDescription(dto.description());
        promo.setDiscountPercent(dto.discountPercent());
        promo.setDiscountMonths(dto.discountMonths());
        promo.setPlanOverride(dto.planOverride() != null ? dto.planOverride().toUpperCase() : null);
        promo.setMaxUses(dto.maxUses());
        promo.setValidTo(dto.validTo());
        if (dto.active() != null) {
            promo.setActive(dto.active());
        }
        return PromoCodeResponse.from(promoCodeRepository.save(promo));
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
    public List<FeatureFlagResponse> getFeatureFlags(String orgId) {
        requireOrg(orgId);
        return featureFlagRepository.findByOrganisationId(orgId).stream()
                .map(FeatureFlagResponse::from)
                .toList();
    }

    @Transactional
    public List<FeatureFlagResponse> updateFeatureFlags(String orgId, List<FeatureFlagDto> dtos) {
        requireOrg(orgId);
        List<FeatureFlagResponse> result = new ArrayList<>();
        for (FeatureFlagDto dto : dtos) {
            FeatureFlag flag = featureFlagRepository
                    .findByOrganisationIdAndFeatureKey(orgId, dto.featureKey())
                    .orElseGet(() -> FeatureFlag.builder()
                            .organisationId(orgId)
                            .featureKey(dto.featureKey())
                            .build());
            flag.setEnabled(dto.enabled());
            result.add(FeatureFlagResponse.from(featureFlagRepository.save(flag)));
        }
        return result;
    }

    // =========================================================================
    // ANNOUNCEMENTS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getAnnouncements() {
        return announcementRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(AnnouncementDto dto) {
        PlatformAnnouncement.Severity severity = parseSeverity(dto.severity());
        PlatformAnnouncement announcement = PlatformAnnouncement.builder()
                .title(dto.title())
                .body(dto.body())
                .severity(severity)
                .active(dto.active())
                .expiresAt(parseExpiresAt(dto.expiresAt()))
                .build();
        return AnnouncementResponse.from(announcementRepository.save(announcement));
    }

    @Transactional
    public AnnouncementResponse updateAnnouncement(String id, AnnouncementDto dto) {
        PlatformAnnouncement ann = announcementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Annonce introuvable : " + id));
        ann.setTitle(dto.title());
        ann.setBody(dto.body());
        ann.setSeverity(parseSeverity(dto.severity()));
        ann.setActive(dto.active());
        ann.setExpiresAt(parseExpiresAt(dto.expiresAt()));
        return AnnouncementResponse.from(announcementRepository.save(ann));
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
        return new ImpersonateResponse(token, org.getNom(), org.getPays(), 30 * 60L);
    }

    @Transactional(readOnly = true)
    public Page<ImpersonationLogResponse> getImpersonationLog(int page, int size) {
        return impersonationLogRepository.findAllByOrderByStartedAtDesc(
                PageRequest.of(page, size))
                .map(ImpersonationLogResponse::from);
    }

    // =========================================================================
    // PLAN TEMPLATES
    // =========================================================================

    @Cacheable("planTemplates")
    @Transactional(readOnly = true)
    public List<PlanTemplateResponse> findAllPlanTemplates() {
        return planTemplateRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(PlanTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanTemplateResponse findPlanTemplate(String id) {
        PlanTemplate template = requirePlanTemplate(id);
        return PlanTemplateResponse.from(template);
    }

    @CacheEvict(value = "planTemplates", allEntries = true)
    @Transactional
    public PlanTemplateResponse createPlanTemplate(PlanTemplateDto dto) {
        String normalizedCode = dto.code().toUpperCase();
        if (planTemplateRepository.existsByCode(normalizedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un plan avec ce code existe déjà : " + normalizedCode);
        }
        PlanTemplate template = PlanTemplate.builder()
                .code(normalizedCode)
                .displayName(dto.displayName())
                .description(dto.description())
                .maxEmployees(dto.maxEmployees())
                .maxSites(dto.maxSites())
                .priceMonthly(dto.priceMonthly())
                .priceAnnual(dto.priceAnnual())
                .trialDays(dto.trialDays())
                .active(dto.active())
                .sortOrder(dto.sortOrder())
                .features(dto.features() != null ? new HashMap<>(dto.features()) : new HashMap<>())
                .build();
        template = planTemplateRepository.save(template);
        log.info("SuperAdmin: created plan template '{}' (code={})", template.getDisplayName(), template.getCode());
        return PlanTemplateResponse.from(template);
    }

    @CacheEvict(value = "planTemplates", allEntries = true)
    @Transactional
    public PlanTemplateResponse updatePlanTemplate(String id, PlanTemplateDto dto) {
        PlanTemplate template = requirePlanTemplate(id);
        String normalizedCode = dto.code().toUpperCase();

        // Guard: code uniqueness, excluding the current entity
        if (planTemplateRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un autre plan utilise déjà ce code : " + normalizedCode);
        }

        template.setCode(normalizedCode);
        template.setDisplayName(dto.displayName());
        template.setDescription(dto.description());
        template.setMaxEmployees(dto.maxEmployees());
        template.setMaxSites(dto.maxSites());
        template.setPriceMonthly(dto.priceMonthly());
        template.setPriceAnnual(dto.priceAnnual());
        template.setTrialDays(dto.trialDays());
        template.setActive(dto.active());
        template.setSortOrder(dto.sortOrder());

        // Replace features map entirely — clear then put to avoid orphan rows
        template.getFeatures().clear();
        if (dto.features() != null) {
            template.getFeatures().putAll(dto.features());
        }

        template = planTemplateRepository.save(template);
        log.info("SuperAdmin: updated plan template '{}' (id={})", template.getCode(), id);
        return PlanTemplateResponse.from(template);
    }

    @CacheEvict(value = "planTemplates", allEntries = true)
    @Transactional
    public void deletePlanTemplate(String id) {
        PlanTemplate template = requirePlanTemplate(id);

        // Refuse deletion when at least one organisation currently uses this plan's code.
        // Only the legacy PlanTier enum values (FREE, STARTER, PRO) can be mapped to
        // subscriptions. CUSTOM or other codes have no matching enum value and can always
        // be deleted safely.
        Subscription.PlanTier matchingTier = resolvePlanTierOrNull(template.getCode());
        if (matchingTier != null && !subscriptionRepository.findByPlanTier(matchingTier).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce plan est actuellement utilisé par une ou plusieurs organisations. "
                    + "Désactivez-le plutôt que de le supprimer.");
        }

        planTemplateRepository.delete(template);
        log.warn("SuperAdmin: DELETED plan template '{}' (id={})", template.getCode(), id);
    }

    private PlanTemplate requirePlanTemplate(String id) {
        return planTemplateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plan template introuvable : " + id));
    }

    /**
     * Attempts to resolve a PlanTemplate code to the legacy PlanTier enum.
     * Returns null when the code has no corresponding enum value (e.g. CUSTOM plans).
     * Used only for the "in-use" guard in deletePlanTemplate.
     */
    private Subscription.PlanTier resolvePlanTierOrNull(String code) {
        try {
            return Subscription.PlanTier.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Organisation requireOrg(String id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Organisation introuvable : " + id));
    }

    /**
     * Single-org summary helper — issues up to 4 individual queries.
     * Acceptable for single-entity operations (findOrganisation, createOrganisation,
     * updateOrgStatus). Do NOT call inside a loop; use findAllOrganisations() instead.
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
                org.getId(),
                org.getNom(),
                org.getStatus(),
                planTier,
                org.getPays(),
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

        String rawToken = CryptoUtil.generateSecureToken();
        String hashedToken = CryptoUtil.sha256(rawToken);
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

    /**
     * Returns true if the organisation's country code corresponds to a French-speaking country.
     * Delegates to {@link LocaleUtils#isFrenchSpeaking(String)} for the actual locale check.
     */
    private boolean resolveIsFrench(Organisation org) {
        return LocaleUtils.isFrenchSpeaking(org.getPays());
    }
}

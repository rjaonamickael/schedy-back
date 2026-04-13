package com.schedy.service;

import com.schedy.dto.request.PlanTemplateDto;
import com.schedy.dto.request.PromoCodeDto;
import com.schedy.dto.request.SubscriptionDto;
import com.schedy.dto.response.PlanTemplateResponse;
import com.schedy.dto.response.PromoCodeResponse;
import com.schedy.dto.response.SubscriptionResponse;
import com.schedy.entity.PlanTemplate;
import com.schedy.entity.PromoCode;
import com.schedy.entity.Subscription;
import com.schedy.repository.PlanTemplateRepository;
import com.schedy.repository.PromoCodeRepository;
import com.schedy.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;

/**
 * Subscriptions, promo codes, and plan template management.
 * Separated from SuperAdminService to fix @Cacheable AOP self-invocation
 * and improve single-responsibility cohesion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialAdminService {

    private final SubscriptionRepository  subscriptionRepository;
    private final PromoCodeRepository     promoCodeRepository;
    private final PlanTemplateRepository  planTemplateRepository;
    private final OrgAdminService         orgAdminService;

    // ── SUBSCRIPTIONS ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(String orgId) {
        Subscription sub = requireSubscription(orgId);
        return SubscriptionResponse.from(sub);
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

    private Subscription requireSubscription(String orgId) {
        orgAdminService.requireOrg(orgId);
        return subscriptionRepository.findByOrganisationId(orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun abonnement trouv\u00e9 pour l'organisation : " + orgId));
    }

    // ── PROMO CODES ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PromoCodeResponse> findAllPromoCodes() {
        return promoCodeRepository.findAll().stream()
                .map(PromoCodeResponse::from).toList();
    }

    @Transactional
    public PromoCodeResponse createPromoCode(PromoCodeDto dto) {
        if (promoCodeRepository.existsByCode(dto.code().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un code promo avec ce code existe d\u00e9j\u00e0 : " + dto.code());
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
        if (!promo.isActive() && (dto.active() == null || !dto.active())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce code promo est d\u00e9sactiv\u00e9. Envoyez active=true pour le r\u00e9activer.");
        }
        promo.setDescription(dto.description());
        promo.setDiscountPercent(dto.discountPercent());
        promo.setDiscountMonths(dto.discountMonths());
        promo.setPlanOverride(dto.planOverride() != null ? dto.planOverride().toUpperCase() : null);
        promo.setMaxUses(dto.maxUses());
        promo.setValidTo(dto.validTo());
        if (dto.active() != null) promo.setActive(dto.active());
        return PromoCodeResponse.from(promoCodeRepository.save(promo));
    }

    @Transactional
    public void deletePromoCode(String id) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Code promo introuvable : " + id));
        promo.setActive(false);
        promoCodeRepository.save(promo);
        log.info("SuperAdmin: promo code '{}' deactivated", promo.getCode());
    }

    @Transactional(readOnly = true)
    public PromoCode validatePromoCode(String code) {
        PromoCode promo = promoCodeRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Code promo invalide : " + code));
        if (!promo.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce code promo est d\u00e9sactiv\u00e9.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce code promo n'est pas encore valide.");
        }
        if (promo.getValidTo() != null && now.isAfter(promo.getValidTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce code promo a expir\u00e9.");
        }
        if (promo.getMaxUses() != null && promo.getCurrentUses() >= promo.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce code promo a atteint son nombre maximal d'utilisations.");
        }
        return promo;
    }

    // ── PLAN TEMPLATES ──────────────────────────────────────────────────────

    @Cacheable("planTemplates")
    @Transactional(readOnly = true)
    public List<PlanTemplateResponse> findAllPlanTemplates() {
        return planTemplateRepository.findAllByOrderBySortOrderAsc().stream()
                .map(PlanTemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlanTemplateResponse findPlanTemplate(String id) {
        return PlanTemplateResponse.from(requirePlanTemplate(id));
    }

    @CacheEvict(value = "planTemplates", allEntries = true)
    @Transactional
    public PlanTemplateResponse createPlanTemplate(PlanTemplateDto dto) {
        String normalizedCode = dto.code().toUpperCase();
        if (planTemplateRepository.existsByCode(normalizedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un plan avec ce code existe d\u00e9j\u00e0 : " + normalizedCode);
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
        if (planTemplateRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un autre plan utilise d\u00e9j\u00e0 ce code : " + normalizedCode);
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
        template.getFeatures().clear();
        if (dto.features() != null) template.getFeatures().putAll(dto.features());
        template = planTemplateRepository.save(template);
        log.info("SuperAdmin: updated plan template '{}' (id={})", template.getCode(), id);
        return PlanTemplateResponse.from(template);
    }

    @CacheEvict(value = "planTemplates", allEntries = true)
    @Transactional
    public void deletePlanTemplate(String id) {
        PlanTemplate template = requirePlanTemplate(id);
        Subscription.PlanTier matchingTier = resolvePlanTierOrNull(template.getCode());
        if (matchingTier != null && !subscriptionRepository.findByPlanTier(matchingTier).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce plan est actuellement utilis\u00e9 par une ou plusieurs organisations. "
                    + "D\u00e9sactivez-le plut\u00f4t que de le supprimer.");
        }
        planTemplateRepository.delete(template);
        log.warn("SuperAdmin: DELETED plan template '{}' (id={})", template.getCode(), id);
    }

    private PlanTemplate requirePlanTemplate(String id) {
        return planTemplateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Plan template introuvable : " + id));
    }

    private Subscription.PlanTier resolvePlanTierOrNull(String code) {
        try { return Subscription.PlanTier.valueOf(code); }
        catch (IllegalArgumentException e) { return null; }
    }
}

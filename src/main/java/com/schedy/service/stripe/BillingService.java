package com.schedy.service.stripe;

import com.schedy.config.StripeProperties;
import com.schedy.config.TenantContext;
import com.schedy.dto.response.BillingSummaryResponse;
import com.schedy.dto.response.CheckoutSessionResponse;
import com.schedy.dto.response.PortalSessionResponse;
import com.schedy.entity.Organisation;
import com.schedy.entity.PlanTemplate;
import com.schedy.entity.Subscription;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.PlanTemplateRepository;
import com.schedy.repository.SubscriptionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates billing flows on top of {@link StripeService}. Owns the
 * lazy customer creation, the checkout/portal session creation, and the
 * computation of the {@link BillingSummaryResponse} the frontend renders.
 *
 * Authorisation: every public method takes the org id from the
 * {@link TenantContext} (extracted from the JWT by the auth filter), never
 * from a request parameter or path variable. This blocks horizontal
 * privilege escalation (admin of org A billing org B).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final OrganisationRepository organisationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanTemplateRepository planTemplateRepository;
    private final StripeService stripeService;
    private final StripeProperties stripeProperties;
    private final TenantContext tenantContext;

    @Value("${schedy.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // ──────────────────────────────────────────────────────────────
    // Read: billing summary (single endpoint feeds the whole tab)
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BillingSummaryResponse getCurrentBillingSummary() {
        String orgId = tenantContext.requireOrganisationId();
        Subscription subscription = subscriptionRepository.findByOrganisationId(orgId).orElse(null);

        if (subscription == null) {
            return defaultTrialSummary();
        }

        boolean isTrialActive = subscription.getStatus() == Subscription.SubscriptionStatus.TRIAL
                && subscription.getTrialEndsAt() != null
                && subscription.getTrialEndsAt().isAfter(OffsetDateTime.now());

        boolean willRenew = subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE
                && !subscription.isCancelAtPeriodEnd();

        String planTemplateCode = subscription.getPlanTier() != null ? subscription.getPlanTier().name() : null;
        String billingInterval = resolveBillingInterval(subscription.getStripePriceId());
        List<String> actions = computeActions(subscription);

        return new BillingSummaryResponse(
                subscription.getPlanTier() != null ? subscription.getPlanTier().name() : null,
                planTemplateCode,
                subscription.getStatus().name(),
                isTrialActive,
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodEnd(),
                subscription.isCancelAtPeriodEnd(),
                willRenew,
                subscription.getMaxEmployees(),
                subscription.getMaxSites(),
                billingInterval,
                subscription.getLatestInvoiceStatus(),
                stripeProperties.isConfigured(),
                actions
        );
    }

    private BillingSummaryResponse defaultTrialSummary() {
        return new BillingSummaryResponse(
                "ESSENTIALS",
                "ESSENTIALS",
                "TRIAL",
                false,
                null, null, false, false,
                15, 1, null, null,
                stripeProperties.isConfigured(),
                stripeProperties.isConfigured() ? List.of("START_CHECKOUT") : List.of()
        );
    }

    private List<String> computeActions(Subscription subscription) {
        List<String> actions = new ArrayList<>();
        if (!stripeProperties.isConfigured()) {
            return actions;
        }
        switch (subscription.getStatus()) {
            case TRIAL, CANCELLED -> actions.add("START_CHECKOUT");
            case ACTIVE, SUSPENDED -> {
                if (subscription.getStripeSubscriptionId() != null) {
                    actions.add("OPEN_PORTAL");
                } else {
                    actions.add("START_CHECKOUT");
                }
            }
        }
        return actions;
    }

    private String resolveBillingInterval(String stripePriceId) {
        if (stripePriceId == null) return null;
        // Resolve the interval from the local PlanTemplate cache to avoid
        // an extra round-trip to Stripe on every summary read.
        return planTemplateRepository.findAll().stream()
                .filter(pt -> stripePriceId.equals(pt.getStripeMonthlyPriceId())
                        || stripePriceId.equals(pt.getStripeAnnualPriceId()))
                .findFirst()
                .map(pt -> stripePriceId.equals(pt.getStripeMonthlyPriceId()) ? "MONTHLY" : "ANNUAL")
                .orElse(null);
    }

    // ──────────────────────────────────────────────────────────────
    // Write: start a Stripe Checkout Session
    // ──────────────────────────────────────────────────────────────

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(String planTemplateCode, String billingInterval) {
        stripeService.ensureConfigured();
        String orgId = tenantContext.requireOrganisationId();

        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation introuvable: " + orgId));

        // Refuse double-checkout when an active sub already exists.
        Optional<Subscription> existing = subscriptionRepository.findByOrganisationId(orgId);
        if (existing.isPresent()
                && existing.get().getStatus() == Subscription.SubscriptionStatus.ACTIVE
                && existing.get().getStripeSubscriptionId() != null) {
            throw new BusinessRuleException("Cette organisation a deja un abonnement actif. Utilisez le portail client pour le gerer.");
        }

        // Resolve the Stripe Price id from the plan template — never trust
        // the frontend with a raw price id.
        PlanTemplate plan = planTemplateRepository.findByCode(planTemplateCode)
                .orElseThrow(() -> new BusinessRuleException("Plan inconnu: " + planTemplateCode));
        String stripePriceId = "ANNUAL".equals(billingInterval)
                ? plan.getStripeAnnualPriceId()
                : plan.getStripeMonthlyPriceId();
        if (stripePriceId == null || stripePriceId.isBlank()) {
            // Server misconfiguration (admin set up the plan but forgot the
            // Stripe price id) — 503 SERVICE_UNAVAILABLE is more honest than
            // 422, which would imply the user did something wrong.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le plan " + planTemplateCode + " n'est pas configure pour la facturation Stripe (" + billingInterval + ").");
        }

        // Lazy-create the Stripe Customer the first time the org engages
        // with billing. Persisted immediately so retries reuse the same id.
        if (org.getStripeCustomerId() == null || org.getStripeCustomerId().isBlank()) {
            String adminEmail = currentUserEmail();
            try {
                Customer customer = stripeService.createCustomer(orgId, org.getNom(), adminEmail);
                org.setStripeCustomerId(customer.getId());
                organisationRepository.save(org);
            } catch (StripeException e) {
                log.error("Stripe customer creation failed for org={}", orgId, e);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe indisponible. Reessayez plus tard.");
            }
        }

        String successUrl = frontendUrl + stripeProperties.successPath();
        String cancelUrl = frontendUrl + stripeProperties.cancelPath();

        try {
            var session = stripeService.createSubscriptionCheckoutSession(
                    org.getStripeCustomerId(), orgId, stripePriceId, successUrl, cancelUrl);
            return new CheckoutSessionResponse(session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed for org={}", orgId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe indisponible. Reessayez plus tard.");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Write: open the Stripe Customer Portal
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PortalSessionResponse createPortalSession() {
        stripeService.ensureConfigured();
        String orgId = tenantContext.requireOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation introuvable: " + orgId));

        if (org.getStripeCustomerId() == null || org.getStripeCustomerId().isBlank()) {
            throw new BusinessRuleException("Aucun client Stripe pour cette organisation. Demarrez d'abord un abonnement.");
        }

        String returnUrl = frontendUrl + stripeProperties.portalReturnPath();
        try {
            var session = stripeService.createPortalSession(org.getStripeCustomerId(), returnUrl);
            return new PortalSessionResponse(session.getUrl());
        } catch (StripeException e) {
            log.error("Stripe portal session creation failed for org={}", orgId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe indisponible. Reessayez plus tard.");
        }
    }

    private String currentUserEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new BusinessRuleException("Aucun utilisateur authentifie.");
        }
        // The JWT principal name IS the email on this stack — no DB lookup needed.
        return auth.getName();
    }
}

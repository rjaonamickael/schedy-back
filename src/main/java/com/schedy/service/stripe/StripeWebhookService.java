package com.schedy.service.stripe;

import com.schedy.entity.Organisation;
import com.schedy.entity.StripeEvent;
import com.schedy.entity.Subscription;
import com.schedy.entity.User;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.StripeEventRepository;
import com.schedy.repository.SubscriptionRepository;
import com.schedy.repository.UserRepository;
import com.schedy.service.EmailService;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Receives a Stripe Event already verified by the controller and applies
 * its side effects to the local database. Idempotency is enforced by the
 * {@code stripe_event} table's primary key on the Stripe event id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final StripeEventRepository stripeEventRepository;
    private final OrganisationRepository organisationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StripeService stripeService;
    // S18-BE-05 — customer notifications (trial_will_end, payment_failed, payment_action_required)
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public boolean handle(Event event) {
        StripeEvent record = StripeEvent.builder()
                .id(event.getId())
                .type(event.getType())
                .receivedAt(OffsetDateTime.now())
                .build();
        try {
            stripeEventRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Stripe webhook duplicate ignored event={} type={}", event.getId(), event.getType());
            return true;
        }

        String organisationId = null;
        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> organisationId = handleCheckoutSessionCompleted(event);
                case "customer.subscription.created",
                     "customer.subscription.updated" -> organisationId = handleSubscriptionUpserted(event);
                case "customer.subscription.deleted" -> organisationId = handleSubscriptionDeleted(event);
                case "customer.subscription.trial_will_end" -> organisationId = handleTrialWillEnd(event);
                case "invoice.paid" -> organisationId = handleInvoicePaid(event);
                case "invoice.payment_failed" -> organisationId = handleInvoicePaymentFailed(event);
                case "invoice.payment_action_required" -> organisationId = handlePaymentActionRequired(event);
                default -> log.info("Stripe webhook ignored unhandled event={} type={}", event.getId(), event.getType());
            }
        } catch (Exception processing) {
            log.error("Stripe webhook handler failed event={} type={}", event.getId(), event.getType(), processing);
            throw processing;
        }

        record.setProcessedAt(OffsetDateTime.now());
        record.setOrganisationId(organisationId);
        stripeEventRepository.save(record);
        log.info("Stripe webhook processed event={} type={} org={}", event.getId(), event.getType(), organisationId);
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    // Event handlers
    // ──────────────────────────────────────────────────────────────

    private String handleCheckoutSessionCompleted(Event event) {
        StripeObject raw = deserialize(event);
        if (!(raw instanceof Session session)) return null;
        
        String orgId = session.getClientReferenceId();
        if (orgId == null) {
            orgId = readMetadataOrgId(session.getMetadata());
        }
        
        if (orgId == null) {
            log.warn("Stripe checkout session has no organisationId session={}", session.getId());
            return null;
        }

        Organisation org = organisationRepository.findById(orgId).orElse(null);
        if (org == null) {
            log.warn("Stripe checkout session refers to unknown org={} session={}", orgId, session.getId());
            return null;
        }
        
        if (org.getStripeCustomerId() == null && session.getCustomer() != null) {
            org.setStripeCustomerId(session.getCustomer());
            organisationRepository.save(org);
        }

        if (session.getSubscription() != null) {
            // FIX: Création d'une variable finale pour la lambda
            final String finalOrgId = orgId;
            Subscription local = subscriptionRepository.findByOrganisationId(finalOrgId)
                    .orElseGet(() -> Subscription.builder()
                            .organisationId(finalOrgId)
                            .planTier(Subscription.PlanTier.ESSENTIALS)
                            .status(Subscription.SubscriptionStatus.ACTIVE)
                            .build());
            local.setStripeSubscriptionId(session.getSubscription());
            local.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(local);
        }
        return orgId;
    }

    private String handleSubscriptionUpserted(Event event) {
        StripeObject raw = deserialize(event);
        if (!(raw instanceof com.stripe.model.Subscription stripeSub)) return null;
        
        String orgId = readMetadataOrgId(stripeSub.getMetadata());
        if (orgId == null) {
            orgId = resolveOrgFromCustomer(stripeSub.getCustomer());
        }
        
        if (orgId == null) {
            log.warn("Stripe subscription event has no resolvable org sub={}", stripeSub.getId());
            return null;
        }

        // FIX: Création d'une variable finale pour la lambda
        final String finalOrgId = orgId;
        Subscription local = subscriptionRepository.findByOrganisationId(finalOrgId)
                .orElseGet(() -> Subscription.builder()
                        .organisationId(finalOrgId)
                        .planTier(Subscription.PlanTier.ESSENTIALS)
                        .build());

        local.setStripeSubscriptionId(stripeSub.getId());
        SubscriptionItem firstItem = firstSubscriptionItem(stripeSub);
        if (firstItem != null) {
            if (firstItem.getPrice() != null) {
                local.setStripePriceId(firstItem.getPrice().getId());
            }
            local.setCurrentPeriodEnd(toOffset(firstItem.getCurrentPeriodEnd()));
        }
        local.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));
        local.setStatus(mapStripeStatus(stripeSub.getStatus()));
        subscriptionRepository.save(local);
        return orgId;
    }

    private SubscriptionItem firstSubscriptionItem(com.stripe.model.Subscription stripeSub) {
        if (stripeSub.getItems() == null || stripeSub.getItems().getData() == null
                || stripeSub.getItems().getData().isEmpty()) {
            return null;
        }
        return stripeSub.getItems().getData().get(0);
    }

    private String handleSubscriptionDeleted(Event event) {
        StripeObject raw = deserialize(event);
        if (!(raw instanceof com.stripe.model.Subscription stripeSub)) return null;
        String orgId = readMetadataOrgId(stripeSub.getMetadata());
        if (orgId == null) orgId = resolveOrgFromCustomer(stripeSub.getCustomer());
        if (orgId == null) return null;

        subscriptionRepository.findByOrganisationId(orgId).ifPresent(local -> {
            local.setStatus(Subscription.SubscriptionStatus.CANCELLED);
            local.setCancelAtPeriodEnd(false);
            local.setStripeSubscriptionId(null);
            local.setMaxEmployees(15);
            local.setMaxSites(1);
            local.setPlanTier(Subscription.PlanTier.ESSENTIALS);
            subscriptionRepository.save(local);
        });
        return orgId;
    }

    private String handleTrialWillEnd(Event event) {
        StripeObject raw = deserialize(event);
        if (!(raw instanceof com.stripe.model.Subscription stripeSub)) return null;
        String orgId = readMetadataOrgId(stripeSub.getMetadata());
        if (orgId == null) orgId = resolveOrgFromCustomer(stripeSub.getCustomer());
        log.info("Stripe trial_will_end org={} sub={} ends={}", orgId, stripeSub.getId(), stripeSub.getTrialEnd());
        if (orgId == null) {
            log.warn("Stripe trial_will_end: no resolvable org, skipping email notification sub={}", stripeSub.getId());
            return null;
        }
        // S18-BE-05 — notify org admin via email
        Instant trialEndsAt = stripeSub.getTrialEnd() != null
                ? Instant.ofEpochSecond(stripeSub.getTrialEnd()) : null;
        sendStripeNotification(orgId, NotificationKind.TRIAL_WILL_END, trialEndsAt, null);
        return orgId;
    }

    private String handleInvoicePaid(Event event) {
        StripeObject raw = deserialize(event);
        if (!(raw instanceof Invoice invoice)) return null;
        String orgId = resolveOrgFromCustomer(invoice.getCustomer());
        if (orgId == null) return null;

        subscriptionRepository.findByOrganisationId(orgId).ifPresent(local -> {
            local.setLatestInvoiceStatus("paid");
            if (invoice.getPeriodEnd() != null) {
                local.setCurrentPeriodEnd(toOffset(invoice.getPeriodEnd()));
            }
            local.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(local);
        });
        return orgId;
    }

    private String handleInvoicePaymentFailed(Event event) {
        StripeObject raw = deserialize(event);
        if (!(raw instanceof Invoice invoice)) return null;
        String orgId = resolveOrgFromCustomer(invoice.getCustomer());
        if (orgId == null) return null;

        subscriptionRepository.findByOrganisationId(orgId).ifPresent(local -> {
            local.setLatestInvoiceStatus("payment_failed");
            subscriptionRepository.save(local);
        });
        log.error("Stripe invoice payment_failed org={} invoice={}", orgId, invoice.getId());
        // S18-BE-05 — notify org admin via email. invoice.getHostedInvoiceUrl() is the
        // Stripe-hosted invoice page; fallback to null (email template falls back to
        // frontendUrl/admin/billing).
        String invoiceUrl = safeHostedInvoiceUrl(invoice);
        sendStripeNotification(orgId, NotificationKind.PAYMENT_FAILED, null, invoiceUrl);
        return orgId;
    }

    private String handlePaymentActionRequired(Event event) {
        StripeObject raw = deserialize(event);
        if (!(raw instanceof Invoice invoice)) return null;
        String orgId = resolveOrgFromCustomer(invoice.getCustomer());
        log.warn("Stripe payment action required (SCA/3DS) org={} invoice={}", orgId, invoice.getId());
        if (orgId == null) {
            log.warn("Stripe payment_action_required: no resolvable org, skipping email notification invoice={}", invoice.getId());
            return null;
        }
        // S18-BE-05 — notify org admin via email
        String invoiceUrl = safeHostedInvoiceUrl(invoice);
        sendStripeNotification(orgId, NotificationKind.PAYMENT_ACTION_REQUIRED, null, invoiceUrl);
        return orgId;
    }

    // ──────────────────────────────────────────────────────────────
    // S18-BE-05 — Email notification helpers
    // ──────────────────────────────────────────────────────────────

    private enum NotificationKind { TRIAL_WILL_END, PAYMENT_FAILED, PAYMENT_ACTION_REQUIRED }

    /**
     * Looks up the org admin (ADMIN role), resolves the org display name,
     * then dispatches the appropriate email via {@link EmailService}. If no
     * ADMIN user exists for the org, logs a warning and returns — no email
     * is sent (never silent-fail to a wrong recipient).
     */
    private void sendStripeNotification(String orgId, NotificationKind kind,
                                        Instant trialEndsAt, String invoiceUrl) {
        String orgName = organisationRepository.findById(orgId)
                .map(Organisation::getNom)
                .orElse(orgId);
        userRepository.findFirstByOrganisationIdAndRole(orgId, User.UserRole.ADMIN)
                .ifPresentOrElse(
                        admin -> dispatchNotification(admin.getEmail(), orgName, kind, trialEndsAt, invoiceUrl),
                        () -> log.warn("Stripe {}: no ADMIN user found for org={}, email not sent", kind, orgId));
    }

    private void dispatchNotification(String email, String orgName, NotificationKind kind,
                                      Instant trialEndsAt, String invoiceUrl) {
        switch (kind) {
            case TRIAL_WILL_END -> emailService.sendStripeTrialWillEndEmail(email, orgName, trialEndsAt);
            case PAYMENT_FAILED -> emailService.sendStripePaymentFailedEmail(email, orgName, invoiceUrl);
            case PAYMENT_ACTION_REQUIRED ->
                    emailService.sendStripePaymentActionRequiredEmail(email, orgName, invoiceUrl);
        }
    }

    /**
     * Defensive wrapper around {@code Invoice.getHostedInvoiceUrl()}. Stripe SDK
     * getter name is stable in v32; this wrapper guards against nulls and
     * future SDK changes without crashing the webhook processing.
     */
    private static String safeHostedInvoiceUrl(Invoice invoice) {
        try {
            String url = invoice.getHostedInvoiceUrl();
            return (url != null && !url.isBlank()) ? url : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private StripeObject deserialize(Event event) {
        return event.getDataObjectDeserializer().getObject().orElse(null);
    }

    private String readMetadataOrgId(java.util.Map<String, String> metadata) {
        if (metadata == null) return null;
        return metadata.get("organisationId");
    }

    private String resolveOrgFromCustomer(String stripeCustomerId) {
        if (stripeCustomerId == null) return null;
        return organisationRepository.findByStripeCustomerId(stripeCustomerId)
                .map(Organisation::getId)
                .orElse(null);
    }

    private Subscription.SubscriptionStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) return Subscription.SubscriptionStatus.TRIAL;
        return switch (stripeStatus) {
            case "trialing" -> Subscription.SubscriptionStatus.TRIAL;
            case "active" -> Subscription.SubscriptionStatus.ACTIVE;
            case "past_due", "unpaid", "incomplete", "incomplete_expired" -> Subscription.SubscriptionStatus.SUSPENDED;
            case "canceled" -> Subscription.SubscriptionStatus.CANCELLED;
            default -> Subscription.SubscriptionStatus.TRIAL;
        };
    }

    private OffsetDateTime toOffset(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds == 0) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }
}
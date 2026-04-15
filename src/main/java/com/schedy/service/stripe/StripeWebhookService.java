package com.schedy.service.stripe;

import com.schedy.entity.Organisation;
import com.schedy.entity.StripeEvent;
import com.schedy.entity.Subscription;
import com.schedy.repository.OrganisationRepository;
import com.schedy.repository.StripeEventRepository;
import com.schedy.repository.SubscriptionRepository;
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
                default -> log.info("Stripe webhook ignored unhandled event={} type={}", event.getId(), event.getType());
            }
        } catch (Exception processing) {
            log.error("Stripe webhook handler failed event={} type={}", event.getId(), event.getType(), processing);
            return false;
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
        log.warn("Stripe invoice payment_failed org={} invoice={}", orgId, invoice.getId());
        return orgId;
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
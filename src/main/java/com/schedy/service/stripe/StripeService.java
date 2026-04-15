package com.schedy.service.stripe;

import com.schedy.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thin wrapper around the Stripe Java SDK. NO business logic, NO database
 * calls. This service exists to (a) keep all SDK calls in one isolated
 * unit, (b) make the SDK trivially mockable in tests, and (c) provide a
 * single throw-point for {@link StripeException} so callers can map it to
 * a HTTP 502.
 *
 * Every method here is side-effect free relative to our own database;
 * orchestration logic (lazy customer creation, persistence, idempotency)
 * lives in {@link BillingService} and {@link StripeWebhookService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeProperties properties;

    /**
     * Creates a Stripe Customer for the given organisation. Stores the
     * Schedy organisation id in {@code metadata.organisationId} so a
     * webhook handler can resolve the org from the customer object alone.
     */
    public Customer createCustomer(String organisationId, String organisationName, String adminEmail) throws StripeException {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(organisationName)
                .setEmail(adminEmail)
                .putMetadata("organisationId", organisationId)
                .build();
        Customer customer = Customer.create(params);
        log.info("Stripe customer created cust={} org={}", customer.getId(), organisationId);
        return customer;
    }

    /**
     * Creates a subscription Checkout Session pre-bound to an existing
     * Customer. {@code clientReferenceId} carries the Schedy organisation
     * id so a webhook handler can resolve the org from the session object.
     */
    public Session createSubscriptionCheckoutSession(
            String stripeCustomerId,
            String organisationId,
            String stripePriceId,
            String successUrl,
            String cancelUrl
    ) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(stripeCustomerId)
                .setClientReferenceId(organisationId)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(stripePriceId)
                        .setQuantity(1L)
                        .build())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata("organisationId", organisationId)
                        .build())
                .putMetadata("organisationId", organisationId)
                .build();
        Session session = Session.create(params);
        log.info("Stripe checkout session created session={} org={} price={}", session.getId(), organisationId, stripePriceId);
        return session;
    }

    /**
     * Creates a one-shot Customer Portal session URL the frontend will
     * redirect to. Portal URLs are short-lived (~5 minutes) and single-use.
     */
    public com.stripe.model.billingportal.Session createPortalSession(String stripeCustomerId, String returnUrl) throws StripeException {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(stripeCustomerId)
                        .setReturnUrl(returnUrl)
                        .build();
        com.stripe.model.billingportal.Session session = com.stripe.model.billingportal.Session.create(params);
        log.info("Stripe portal session created session={} cust={}", session.getId(), stripeCustomerId);
        return session;
    }

    /**
     * Retrieves a Stripe Subscription with default expansions. Used by the
     * webhook handler to refresh local state and by reconciliation jobs.
     */
    public Subscription retrieveSubscription(String stripeSubscriptionId) throws StripeException {
        SubscriptionRetrieveParams params = SubscriptionRetrieveParams.builder().build();
        return Subscription.retrieve(stripeSubscriptionId, params, null);
    }

    /**
     * Verifies a webhook payload's HMAC signature against the configured
     * webhook secret. The payload MUST be the exact raw bytes Stripe sent
     * (no JSON re-serialization), otherwise the signature check fails.
     *
     * @throws SignatureVerificationException when the signature is invalid
     *         (controller maps to HTTP 400 — Stripe will not retry).
     */
    public Event verifyAndConstructEvent(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, properties.webhookSecret());
    }

    /**
     * Defensive guard so callers do not need to re-check at every call site.
     * Returns 503 SERVICE_UNAVAILABLE when the SDK was not initialised at boot —
     * the frontend can degrade the billing tab to a "configurez Stripe" notice
     * without crashing.
     */
    public void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le module de facturation n'est pas configure sur ce serveur.");
        }
    }
}

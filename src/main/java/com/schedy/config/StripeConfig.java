package com.schedy.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Initialises the Stripe Java SDK once at boot. {@code Stripe.apiKey} is a
 * static field on the SDK class and must be set exactly once before any
 * SDK call. We never log the key — only whether it is configured and which
 * mode (test vs live).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
@RequiredArgsConstructor
public class StripeConfig {

    private final StripeProperties properties;

    @PostConstruct
    public void init() {
        if (!properties.isConfigured()) {
            log.warn("Stripe SDK NOT initialised — schedy.stripe.secret-key is blank. Billing endpoints will return 503.");
            return;
        }
        Stripe.apiKey = properties.secretKey();

        // La version de l'API Stripe est déterminée par la version de la dépendance stripe-java.
        // Il est maintenant possible de la lire via la constante Stripe.API_VERSION.
        log.info("Stripe SDK initialised in {} mode (API version: {})",
                properties.secretKey().startsWith("sk_live_") ? "LIVE" : "TEST",
                Stripe.API_VERSION); // Affichera "2026-03-25.dahlia"

        if (!properties.isWebhookConfigured()) {
            log.warn("Stripe webhook secret is blank — incoming webhooks will be rejected with 503.");
        }
    }
}

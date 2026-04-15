package com.schedy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe SDK configuration. Bound from {@code schedy.stripe.*} in
 * application.yml. All fields are loaded from environment variables in
 * production; blank values disable the billing module gracefully so the
 * rest of the app boots normally during local development.
 */
@ConfigurationProperties(prefix = "schedy.stripe")
public record StripeProperties(
        String secretKey,
        String webhookSecret,
        String successPath,
        String cancelPath,
        String portalReturnPath
) {
    /** True when the SDK secret key is present and looks like a Stripe key. */
    public boolean isConfigured() {
        return secretKey != null
                && !secretKey.isBlank()
                && (secretKey.startsWith("sk_test_") || secretKey.startsWith("sk_live_"));
    }

    /** True when webhook handling is operational (signature verification). */
    public boolean isWebhookConfigured() {
        return webhookSecret != null
                && !webhookSecret.isBlank()
                && webhookSecret.startsWith("whsec_");
    }
}

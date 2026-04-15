package com.schedy.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Single-payload billing snapshot the frontend renders the Abonnement tab
 * from. All derived state (willRenew, isTrialActive, availableActions) is
 * computed server-side so the Angular component stays presentational.
 *
 * Stripe identifiers are intentionally NOT exposed — only mirrored state.
 */
public record BillingSummaryResponse(
        String planTier,
        String planTemplateCode,
        String status,
        boolean isTrialActive,
        OffsetDateTime trialEndsAt,
        OffsetDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        boolean willRenew,
        int maxEmployees,
        int maxSites,
        String billingInterval,
        String latestInvoiceStatus,
        boolean stripeConfigured,
        List<String> availableActions
) {}

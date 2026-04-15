package com.schedy.controller;

import com.schedy.dto.request.CreateCheckoutSessionRequest;
import com.schedy.dto.response.BillingSummaryResponse;
import com.schedy.dto.response.CheckoutSessionResponse;
import com.schedy.dto.response.PortalSessionResponse;
import com.schedy.service.stripe.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin self-service billing endpoints. The org id is taken from the JWT,
 * never from a path or body parameter — this prevents one admin from
 * billing another organisation.
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class BillingController {

    private final BillingService billingService;

    /**
     * GET /api/v1/billing/summary
     * Returns the full billing snapshot for the admin's own organisation.
     */
    @GetMapping("/summary")
    public ResponseEntity<BillingSummaryResponse> getSummary() {
        return ResponseEntity.ok(billingService.getCurrentBillingSummary());
    }

    /**
     * POST /api/v1/billing/checkout-session
     * Creates a Stripe Checkout Session for the requested plan + interval
     * and returns the URL the frontend redirects to.
     */
    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request) {
        CheckoutSessionResponse response = billingService.createCheckoutSession(
                request.planTemplateCode(), request.billingInterval());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/billing/portal-session
     * Creates a Stripe Customer Portal session and returns its short-lived
     * URL. The admin uses the portal to update payment methods, switch
     * plan, or cancel the subscription.
     */
    @PostMapping("/portal-session")
    public ResponseEntity<PortalSessionResponse> createPortalSession() {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.createPortalSession());
    }
}

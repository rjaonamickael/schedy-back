package com.schedy.controller;

import com.schedy.config.StripeProperties;
import com.schedy.service.stripe.StripeService;
import com.schedy.service.stripe.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook receiver. The path is whitelisted in {@code SecurityConfig}
 * (signature verification IS the authentication — Stripe cannot present a
 * JWT). The request body MUST be received as a raw {@code String} so the
 * HMAC signature verification matches the exact bytes Stripe signed; never
 * deserialize to a Map or POJO before calling {@code constructEvent()}.
 *
 * Response policy:
 *   200 — event accepted (or recognised as a duplicate)
 *   400 — signature invalid (Stripe will NOT retry — correct, it is forged)
 *   500 — handler crashed (Stripe will retry with exponential backoff)
 *   503 — webhook secret not configured
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/billing/webhook")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeService stripeService;
    private final StripeWebhookService stripeWebhookService;
    private final StripeProperties stripeProperties;

    @PostMapping
    public ResponseEntity<String> receive(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        if (!stripeProperties.isWebhookConfigured()) {
            log.warn("Stripe webhook received but webhook secret is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhook_not_configured");
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Stripe webhook missing Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing_signature");
        }

        Event event;
        try {
            event = stripeService.verifyAndConstructEvent(payload, signature);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid_signature");
        } catch (Exception parsing) {
            log.warn("Stripe webhook payload parse failed: {}", parsing.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid_payload");
        }

        boolean processed = stripeWebhookService.handle(event);
        if (!processed) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("handler_failed");
        }
        return ResponseEntity.ok("ok");
    }
}

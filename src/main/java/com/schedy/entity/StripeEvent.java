package com.schedy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Webhook idempotency record. The Stripe event id (evt_xxx) is the primary
 * key, so two concurrent deliveries of the same event collide on insert and
 * the loser short-circuits to a 200 acknowledgment without re-processing.
 *
 * processed_at is null between the initial insert and the successful commit
 * of the handler. A scheduled job can find rows older than ~10 minutes with
 * processed_at IS NULL and reconcile them by re-fetching from Stripe.
 */
@Entity
@Table(name = "stripe_event")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StripeEvent {

    @Id
    @Column(name = "id", length = 255, nullable = false, updatable = false)
    private String id;

    @Column(name = "type", length = 100, nullable = false)
    private String type;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "organisation_id", length = 255)
    private String organisationId;
}

package com.schedy.repository;

import com.schedy.entity.StripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {

    /**
     * Returns stripe_event records that were received before {@code cutoff}
     * but whose processing never completed ({@code processed_at IS NULL}).
     * Used by {@link com.schedy.service.stripe.StripeEventReconciliationScheduler}
     * to surface orphaned events for manual review.
     */
    List<StripeEvent> findByProcessedAtIsNullAndReceivedAtBefore(OffsetDateTime cutoff);
}

package com.schedy.service.stripe;

import com.schedy.entity.StripeEvent;
import com.schedy.repository.StripeEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Hourly reconciliation job for the {@code stripe_event} idempotency table.
 *
 * <p>When {@link StripeWebhookService#handle(com.stripe.model.Event)} throws
 * after the initial {@code INSERT} but before setting {@code processed_at}, the
 * transaction rolls back — leaving the row with {@code processed_at IS NULL}.
 * Stripe will re-deliver most events, but edge cases (very short Stripe retry
 * windows, partial DB failures, node restarts) can leave permanent orphans.</p>
 *
 * <p>This job surfaces those orphans via structured log lines at ERROR level
 * and marks them as reconciled so they stop appearing in future runs.
 * It does <b>NOT</b> re-process events automatically — duplicate billing
 * side effects (double-activating a subscription, double-crediting a period)
 * are far worse than a manual fix.</p>
 *
 * <p>Runs every hour at :30 (offset from {@link com.schedy.service.CongesScheduler}'s
 * 02:00 to avoid DB contention spikes). Protected by ShedLock so only one
 * node executes in a multi-instance deployment.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripeEventReconciliationScheduler {

    private final StripeEventRepository stripeEventRepository;

    /**
     * The grace period before an unprocessed event is considered orphaned.
     * 10 minutes avoids a race with an active webhook handler still inside
     * its transaction window.
     */
    private static final int ORPHAN_GRACE_MINUTES = 10;

    /**
     * Sentinel value written to {@code organisation_id} so the row is clearly
     * flagged in the DB without requiring a separate column. The real
     * {@code organisation_id} was never set (the handler never reached that
     * point), so no meaningful data is lost.
     */
    static final String RECONCILED_SENTINEL = "RECONCILED_ORPHAN";

    @Scheduled(cron = "0 30 * * * ?")
    @SchedulerLock(name = "stripeEvent_reconciliation", lockAtLeastFor = "5m", lockAtMostFor = "30m")
    public void reconcileOrphanedEvents() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(ORPHAN_GRACE_MINUTES);
        List<StripeEvent> orphaned =
                stripeEventRepository.findByProcessedAtIsNullAndReceivedAtBefore(cutoff);

        if (orphaned.isEmpty()) {
            log.debug("Stripe reconciliation: no orphaned events found (cutoff={})", cutoff);
            return;
        }

        log.warn("Stripe reconciliation: found {} orphaned event(s) received before {} — manual review required",
                orphaned.size(), cutoff);

        for (StripeEvent event : orphaned) {
            flagOrphan(event);
        }

        log.info("Stripe reconciliation: marked {} orphaned event(s) as reconciled", orphaned.size());
    }

    /**
     * Marks a single orphaned event as reconciled and logs it at ERROR level
     * so it appears in any log-based alert pipeline.
     *
     * Each event is saved in its own transaction so a transient DB error on
     * one record does not block the others (mirrors the per-org transaction
     * strategy used in CongesScheduler).
     */
    @Transactional
    void flagOrphan(StripeEvent event) {
        try {
            log.error("RECONCILIATION orphaned stripe_event id={} type={} received={} — investigate and re-process manually if needed",
                    event.getId(), event.getType(), event.getReceivedAt());

            event.setProcessedAt(OffsetDateTime.now());
            event.setOrganisationId(RECONCILED_SENTINEL);
            stripeEventRepository.save(event);
        } catch (Exception e) {
            log.error("Stripe reconciliation: failed to flag orphan id={}: {}", event.getId(), e.getMessage(), e);
        }
    }
}

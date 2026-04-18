package com.schedy.service;

import com.schedy.entity.PointageCode;
import com.schedy.repository.PointageCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Checks every 60 seconds for active pointage codes whose validity window has expired
 * and issues a fresh rotation for each one.
 *
 * <h2>IMPORTANT — DO NOT add {@code @Transactional} on this method</h2>
 *
 * <p>This method must NOT be wrapped in any transaction (read-only or otherwise).
 * The previous version was annotated {@code @Transactional(readOnly = true)} with
 * the intent of optimising the read snapshot. That broke rotation entirely : the
 * inner {@link PointageCodeService#createForSiteInternal} call is annotated
 * {@code @Transactional} (default propagation REQUIRED), so it joined the outer
 * read-only transaction. PostgreSQL then refused every {@code INSERT}/{@code UPDATE}
 * with "cannot execute UPDATE in a read-only transaction", the catch block
 * swallowed the failure, and the scheduler tracked many "rotations attempted" while
 * persisting absolutely nothing. End users observed expired codes that never rotated
 * unless they reconnected and triggered a manual regeneration.
 *
 * <p>The read query is safe without an explicit transaction because :
 * <ol>
 *   <li>Spring Data JPA already wraps every repository method in a per-call
 *       {@code @Transactional(readOnly = true)} via {@code SimpleJpaRepository},
 *       so the read-only optimisation is preserved without an outer wrapper.</li>
 *   <li>It is a single statement — PostgreSQL's MVCC gives the SELECT a consistent
 *       snapshot atomically, so isolation level worries do not apply here.</li>
 * </ol>
 *
 * <p>Each iteration of the loop relies on {@code createForSiteInternal}'s own
 * transaction. Failures stay isolated per site — one bad site cannot roll back
 * the others.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PointageCodeRotationScheduler {

    private final PointageCodeRepository repository;
    private final PointageCodeService service;

    @Scheduled(fixedDelay = 60_000) // every 60 seconds
    @SchedulerLock(name = "pointageCode_rotation", lockAtLeastFor = "30s", lockAtMostFor = "5m")
    public void rotateExpiredCodes() {
        // Read in Spring Data JPA's auto-wrapped read-only tx (no outer @Transactional here !!).
        List<PointageCode> expired = repository.findByActifTrueAndValidToBefore(
                OffsetDateTime.now(ZoneOffset.UTC));

        if (expired.isEmpty()) {
            return;
        }

        int rotated = 0;
        for (PointageCode code : expired) {
            try {
                // Each call gets its own write transaction via the inner @Transactional.
                service.createForSiteInternal(
                        code.getSiteId(),
                        code.getRotationValeur(),
                        code.getRotationUnite(),
                        code.getOrganisationId()
                );
                rotated++;
            } catch (Exception e) {
                // Log and continue — one failing site must not block others
                log.error("Failed to rotate code for site {} (org {}): {}",
                        code.getSiteId(), code.getOrganisationId(), e.getMessage(), e);
            }
        }

        log.info("Rotation cycle: {} expired code(s) found, {} rotated successfully",
                expired.size(), rotated);
    }
}

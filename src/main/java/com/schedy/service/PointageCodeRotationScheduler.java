package com.schedy.service;

import com.schedy.entity.PointageCode;
import com.schedy.repository.PointageCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Checks every 60 seconds for active pointage codes whose validity window has expired
 * and issues a fresh rotation for each one.
 *
 * The rotation delegates to {@link PointageCodeService#createForSiteInternal}, which
 * owns its own transaction per site — keeping failures isolated so one bad site cannot
 * roll back all others. The read query here runs outside any transaction, which is
 * intentional and safe for a non-critical read-then-iterate pattern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PointageCodeRotationScheduler {

    private final PointageCodeRepository repository;
    private final PointageCodeService service;

    @Scheduled(fixedDelay = 60_000) // every 60 seconds
    public void rotateExpiredCodes() {
        List<PointageCode> expired = repository.findByActifTrueAndValidToBefore(
                OffsetDateTime.now(ZoneOffset.UTC));

        for (PointageCode code : expired) {
            try {
                service.createForSiteInternal(
                        code.getSiteId(),
                        code.getRotationValeur(),
                        code.getRotationUnite(),
                        code.getOrganisationId()
                );
            } catch (Exception e) {
                // Log and continue — one failing site must not block others
                log.error("Failed to rotate code for site {} (org {}): {}",
                        code.getSiteId(), code.getOrganisationId(), e.getMessage(), e);
            }
        }

        if (!expired.isEmpty()) {
            log.info("Rotated {} expired pointage code(s)", expired.size());
        }
    }
}

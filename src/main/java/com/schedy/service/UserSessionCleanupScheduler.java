package com.schedy.service;

import com.schedy.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * S18-BE-01 — Daily scheduler that removes {@link com.schedy.entity.UserSession}
 * rows whose {@code expiresAt} is in the past. Without this job, the
 * {@code user_session} table grows indefinitely because revoked / expired
 * sessions are never garbage-collected by the refresh-rotation path (rotation
 * deletes the old row, but natural expiration only marks
 * {@code expiresAt < now}, it does not delete).
 *
 * <p>Runs daily at 03:00 — intentionally offset from
 * {@link CongesScheduler#runDailyCongesJob()} (02:00) and
 * {@code PointageCodeRotationScheduler} so the three jobs do not contend on
 * the same Postgres connection pool at once.</p>
 *
 * <p>Wrapped by {@link SchedulerLock} (ShedLock 6.3.1 + V46 {@code shedlock}
 * table) so a future multi-instance deployment only runs the cleanup once.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserSessionCleanupScheduler {

    private final UserSessionRepository sessionRepository;

    /**
     * Deletes every row where {@code expires_at < NOW()}. Idempotent — a
     * second run the same night is a no-op on already-pruned rows. The
     * repository method is annotated {@code @Modifying @Transactional}, so
     * the DELETE statement and commit are atomic per invocation.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "userSessionCleanup_dailyJob", lockAtLeastFor = "5m", lockAtMostFor = "30m")
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        try {
            int removed = sessionRepository.deleteExpiredSessions(now);
            if (removed > 0) {
                log.info("UserSessionCleanup: removed {} expired session(s) before {}", removed, now);
            } else {
                log.debug("UserSessionCleanup: no expired sessions to remove at {}", now);
            }
        } catch (RuntimeException e) {
            log.error("UserSessionCleanup failed: {}", e.getMessage(), e);
        }
    }
}

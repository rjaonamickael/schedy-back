package com.schedy.service;

import com.schedy.repository.UserSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S18-BE-01 — Unit tests for the session-cleanup scheduler.
 */
@ExtendWith(MockitoExtension.class)
class UserSessionCleanupSchedulerTest {

    @Mock
    private UserSessionRepository sessionRepository;

    @InjectMocks
    private UserSessionCleanupScheduler scheduler;

    @Test
    @DisplayName("cleanupExpiredSessions invokes deleteExpiredSessions with a timestamp in the last second")
    void cleanupExpiredSessions_callsRepositoryWithCurrentInstant() {
        when(sessionRepository.deleteExpiredSessions(any(Instant.class))).thenReturn(5);
        Instant before = Instant.now();

        scheduler.cleanupExpiredSessions();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(sessionRepository).deleteExpiredSessions(captor.capture());
        Instant captured = captor.getValue();
        Instant after = Instant.now();
        assertThat(captured).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    @DisplayName("cleanupExpiredSessions swallows repository exceptions and logs them")
    void cleanupExpiredSessions_swallowsRuntimeException() {
        when(sessionRepository.deleteExpiredSessions(any(Instant.class)))
                .thenThrow(new RuntimeException("db down"));

        // The scheduler must not let the scheduled thread die with an exception
        scheduler.cleanupExpiredSessions();

        verify(sessionRepository).deleteExpiredSessions(any(Instant.class));
    }

    @Test
    @DisplayName("cleanupExpiredSessions works with zero deletions (no-op branch)")
    void cleanupExpiredSessions_zeroDeletions() {
        when(sessionRepository.deleteExpiredSessions(any(Instant.class))).thenReturn(0);

        scheduler.cleanupExpiredSessions();

        verify(sessionRepository).deleteExpiredSessions(any(Instant.class));
    }
}

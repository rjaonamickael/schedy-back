package com.schedy.service;

import com.schedy.entity.PinAuditLog;
import com.schedy.repository.PinAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V36: helper service that writes PIN lifecycle audit log entries.
 *
 * <p>Audit writes participate in the CALLER's transaction (default
 * {@code REQUIRED} propagation): if the audit save fails, the calling PIN
 * operation rolls back too. Compliance demands that no PIN change can be
 * visible without its corresponding audit row, and vice versa.
 *
 * <p>Centralising the write here gives a single place to add cross-cutting
 * concerns later (metrics, async publishing, retention policies) without
 * touching every PIN call site.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PinAuditLogger {

    private final PinAuditLogRepository repository;

    @Transactional
    public void write(PinAuditLog entry) {
        repository.save(entry);
        log.debug("PIN audit entry recorded: action={} employe={} org={}",
                entry.getAction(), entry.getEmployeId(), entry.getOrganisationId());
    }
}

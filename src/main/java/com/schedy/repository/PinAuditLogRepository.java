package com.schedy.repository;

import com.schedy.entity.PinAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PinAuditLogRepository extends JpaRepository<PinAuditLog, String> {

    /** Returns the most-recent-first audit trail for a single employee. */
    List<PinAuditLog> findByEmployeIdOrderByTimestampDesc(String employeId);

    /** Org-wide audit feed for compliance export (paginated). */
    Page<PinAuditLog> findByOrganisationIdOrderByTimestampDesc(
            String organisationId, Pageable pageable);

    /** Counts events of a given action for a single employee (e.g. PRINT count). */
    long countByEmployeIdAndAction(String employeId, PinAuditLog.Action action);
}

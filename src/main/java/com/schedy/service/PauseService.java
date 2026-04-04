package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.entity.Pause;
import com.schedy.entity.StatutPause;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.PauseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PauseService {

    private final PauseRepository pauseRepository;
    private final TenantContext tenantContext;

    @Transactional(readOnly = true)
    public List<Pause> findByDate(String date) {
        return findByDate(date, null);
    }

    /**
     * Returns pauses for the caller's organisation on the given date.
     * When {@code siteId} is non-null the result is narrowed to that site only,
     * enabling multi-site dashboards without breaking single-site callers.
     */
    @Transactional(readOnly = true)
    public List<Pause> findByDate(String date, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        LocalDate ld = LocalDate.parse(date);
        OffsetDateTime startOfDay = ld.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endOfDay = ld.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);

        if (siteId != null && !siteId.isBlank()) {
            return pauseRepository.findBySiteIdAndOrganisationIdAndDebutBetween(
                    siteId, orgId, startOfDay, endOfDay);
        }
        return pauseRepository.findByOrganisationIdAndDebutBetween(orgId, startOfDay, endOfDay);
    }

    @Transactional
    public Pause confirmer(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Pause pause = pauseRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pause", id));

        if (pause.getStatut() != StatutPause.DETECTE) {
            throw new BusinessRuleException("Seules les pauses détectées peuvent être confirmées");
        }

        String caller = SecurityContextHolder.getContext().getAuthentication().getName();
        pause.setStatut(StatutPause.CONFIRME);
        pause.setConfirmeParId(caller);
        pause.setConfirmeAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pauseRepository.save(pause);
    }

    @Transactional
    public Pause contester(String id, String motif) {
        String orgId = tenantContext.requireOrganisationId();
        Pause pause = pauseRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Pause", id));

        if (pause.getStatut() != StatutPause.DETECTE) {
            throw new BusinessRuleException("Seules les pauses détectées peuvent être contestées");
        }

        String caller = SecurityContextHolder.getContext().getAuthentication().getName();
        pause.setStatut(StatutPause.CONTESTE);
        pause.setMotifContestation(motif);
        pause.setConfirmeParId(caller);
        pause.setConfirmeAt(OffsetDateTime.now(ZoneOffset.UTC));
        return pauseRepository.save(pause);
    }
}

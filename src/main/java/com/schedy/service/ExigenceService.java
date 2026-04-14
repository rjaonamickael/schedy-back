package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.ExigenceDto;
import com.schedy.entity.Exigence;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.ExigenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExigenceService {

    private final ExigenceRepository exigenceRepository;
    private final TenantContext tenantContext;

    @Transactional(readOnly = true)
    public List<Exigence> findAll() {
        String orgId = tenantContext.requireOrganisationId();
        return exigenceRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public Page<Exigence> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return exigenceRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public Exigence findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return exigenceRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Exigence", id));
    }

    @Transactional(readOnly = true)
    public List<Exigence> findByRole(String role) {
        String orgId = tenantContext.requireOrganisationId();
        return exigenceRepository.findByRoleAndOrganisationId(role, orgId);
    }

    @Transactional(readOnly = true)
    public List<Exigence> findBySiteId(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return exigenceRepository.findBySiteIdAndOrganisationId(siteId, orgId);
    }

    @Transactional(readOnly = true)
    public Page<Exigence> findBySiteId(String siteId, Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return exigenceRepository.findBySiteIdAndOrganisationId(siteId, orgId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Exigence> findByRoleAndSiteId(String role, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return exigenceRepository.findByRoleAndSiteIdAndOrganisationId(role, siteId, orgId);
    }

    @Transactional
    public Exigence create(ExigenceDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Exigence exigence = Exigence.builder()
                .libelle(dto.libelle())
                .jours(dto.jours() != null ? dto.jours() : Collections.emptyList())
                .heureDebut(dto.heureDebut())
                .heureFin(dto.heureFin())
                .role(dto.role())
                .nombreRequis(dto.nombreRequis())
                .siteId(dto.siteId())
                .organisationId(orgId)
                .dateDebut(dto.dateDebut())
                .dateFin(dto.dateFin())
                .priorite(dto.priorite() != null ? dto.priorite() : 0)
                .build();
        return exigenceRepository.save(exigence);
    }

    @Transactional
    public Exigence update(String id, ExigenceDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Exigence exigence = exigenceRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Exigence", id));
        exigence.setLibelle(dto.libelle());
        if (dto.jours() != null) {
            exigence.getJours().clear();
            exigence.getJours().addAll(dto.jours());
        }
        exigence.setHeureDebut(dto.heureDebut());
        exigence.setHeureFin(dto.heureFin());
        exigence.setRole(dto.role());
        exigence.setNombreRequis(dto.nombreRequis());
        exigence.setSiteId(dto.siteId());
        exigence.setDateDebut(dto.dateDebut());
        exigence.setDateFin(dto.dateFin());
        exigence.setPriorite(dto.priorite() != null ? dto.priorite() : 0);
        return exigenceRepository.save(exigence);
    }

    /**
     * Sprint 16 / Feature 1 : returns the list of exigences that apply on the
     * given reference date, after deduplication by {@code (role, siteId, jour, heureDebut, heureFin)}
     * using {@link Exigence#getPriorite()} as the tiebreaker (highest wins).
     *
     * <p>Rules :</p>
     * <ul>
     *   <li>An exigence with null {@code dateDebut} is always active.</li>
     *   <li>An exigence with non-null {@code dateDebut}/{@code dateFin} is active only
     *       when {@code dateDebut <= reference <= dateFin}.</li>
     *   <li>When two exigences cover the same tuple, the higher {@link Exigence#getPriorite()}
     *       wins. Base exigences stay at 0; period overrides use positive integers.</li>
     * </ul>
     *
     * <p>Called by {@code AutoAffectationService.loadExigences()} with the week's Monday
     * as the reference date. The solver sees exactly one exigence per tuple.</p>
     */
    @Transactional(readOnly = true)
    public List<Exigence> findActiveForDate(LocalDate reference) {
        String orgId = tenantContext.requireOrganisationId();
        List<Exigence> all = exigenceRepository.findByOrganisationId(orgId);

        // Filter by date range
        List<Exigence> applicables = new ArrayList<>();
        for (Exigence e : all) {
            if (e.getDateDebut() == null) {
                applicables.add(e); // base exigence, always active
                continue;
            }
            boolean afterStart = !reference.isBefore(e.getDateDebut());
            boolean beforeEnd  = e.getDateFin() == null || !reference.isAfter(e.getDateFin());
            if (afterStart && beforeEnd) {
                applicables.add(e);
            }
        }

        // Deduplicate by tuple, keeping highest priorite
        Map<String, Exigence> byTuple = new HashMap<>();
        for (Exigence e : applicables) {
            String tuple = buildTupleKey(e);
            Exigence existing = byTuple.get(tuple);
            if (existing == null || e.getPriorite() > existing.getPriorite()) {
                byTuple.put(tuple, e);
            }
        }
        return new ArrayList<>(byTuple.values());
    }

    private static String buildTupleKey(Exigence e) {
        return (e.getRole() == null ? "" : e.getRole())
                + "|" + (e.getSiteId() == null ? "" : e.getSiteId())
                + "|" + (e.getJours() == null ? "" : e.getJours().toString())
                + "|" + e.getHeureDebut()
                + "|" + e.getHeureFin();
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Exigence exigence = exigenceRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Exigence", id));
        exigenceRepository.delete(exigence);
    }
}

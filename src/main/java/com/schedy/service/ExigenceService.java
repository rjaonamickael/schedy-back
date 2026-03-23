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

import java.util.Collections;
import java.util.List;

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
        return exigenceRepository.save(exigence);
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Exigence exigence = exigenceRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Exigence", id));
        exigenceRepository.delete(exigence);
    }
}

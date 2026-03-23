package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.CreneauAssigneDto;
import com.schedy.entity.CreneauAssigne;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.CreneauAssigneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanningService {

    private final CreneauAssigneRepository creneauRepository;
    private final TenantContext tenantContext;

    @Transactional(readOnly = true)
    public Page<CreneauAssigne> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public CreneauAssigne findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", id));
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findBySemaine(String semaine) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findBySemaineAndOrganisationId(semaine, orgId);
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findBySemaineAndSite(String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findBySemaineAndSiteIdAndOrganisationId(semaine, siteId, orgId);
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeId(String employeId) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findByEmployeIdAndOrganisationId(employeId, orgId);
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeIdAndSite(String employeId, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findByEmployeIdAndSiteIdAndOrganisationId(employeId, siteId, orgId);
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeIdAndSemaine(String employeId, String semaine) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findByEmployeIdAndSemaineAndOrganisationId(employeId, semaine, orgId);
    }

    @Transactional(readOnly = true)
    public List<CreneauAssigne> findByEmployeIdAndSemaineAndSite(String employeId, String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findByEmployeIdAndSemaineAndSiteIdAndOrganisationId(employeId, semaine, siteId, orgId);
    }

    @Transactional(readOnly = true)
    public Page<CreneauAssigne> findBySite(String siteId, Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return creneauRepository.findBySiteIdAndOrganisationId(siteId, orgId, pageable);
    }

    @Transactional
    public CreneauAssigne create(CreneauAssigneDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        CreneauAssigne creneau = CreneauAssigne.builder()
                .employeId(dto.employeId())
                .jour(dto.jour())
                .heureDebut(dto.heureDebut())
                .heureFin(dto.heureFin())
                .semaine(dto.semaine())
                .siteId(dto.siteId())
                .organisationId(orgId)
                .build();
        return creneauRepository.save(creneau);
    }

    @Transactional
    public List<CreneauAssigne> createBatch(List<CreneauAssigneDto> dtos) {
        String orgId = tenantContext.requireOrganisationId();
        List<CreneauAssigne> creneaux = dtos.stream().map(dto ->
                CreneauAssigne.builder()
                        .employeId(dto.employeId())
                        .jour(dto.jour())
                        .heureDebut(dto.heureDebut())
                        .heureFin(dto.heureFin())
                        .semaine(dto.semaine())
                        .siteId(dto.siteId())
                        .organisationId(orgId)
                        .build()
        ).toList();
        return creneauRepository.saveAll(creneaux);
    }

    @Transactional
    public CreneauAssigne update(String id, CreneauAssigneDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        CreneauAssigne creneau = creneauRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", id));
        creneau.setEmployeId(dto.employeId());
        creneau.setJour(dto.jour());
        creneau.setHeureDebut(dto.heureDebut());
        creneau.setHeureFin(dto.heureFin());
        creneau.setSemaine(dto.semaine());
        creneau.setSiteId(dto.siteId());
        return creneauRepository.save(creneau);
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        CreneauAssigne creneau = creneauRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau", id));
        creneauRepository.delete(creneau);
    }

    @Transactional
    public void deleteBySemaine(String semaine) {
        String orgId = tenantContext.requireOrganisationId();
        creneauRepository.deleteBySemaineAndOrganisationId(semaine, orgId);
    }

    @Transactional
    public void deleteBySemaineAndSite(String semaine, String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        creneauRepository.deleteBySemaineAndSiteIdAndOrganisationId(semaine, siteId, orgId);
    }
}

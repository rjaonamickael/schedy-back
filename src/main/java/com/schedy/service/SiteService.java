package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.SiteDto;
import com.schedy.entity.PointageCode.FrequenceRotation;
import com.schedy.entity.Site;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private final SiteRepository siteRepository;
    private final TenantContext tenantContext;
    private final PointageCodeService pointageCodeService;

    @Transactional(readOnly = true)
    public Page<Site> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return siteRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Site> findAll() {
        String orgId = tenantContext.requireOrganisationId();
        return siteRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public List<Site> findAllActifs() {
        String orgId = tenantContext.requireOrganisationId();
        return siteRepository.findByOrganisationIdAndActifTrue(orgId);
    }

    @Transactional(readOnly = true)
    public Site findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return siteRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id));
    }

    @Transactional
    public Site create(SiteDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        if (siteRepository.existsByNomAndOrganisationId(dto.nom(), orgId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un site avec ce nom existe deja");
        }
        Site site = new Site();
        site.setNom(dto.nom());
        site.setAdresse(dto.adresse());
        site.setTelephone(dto.telephone());
        site.setOrganisationId(orgId);
        site.setActif(dto.actif());
        site = siteRepository.save(site);

        // Auto-create a PointageCode for the new site
        try {
            pointageCodeService.createForSiteInternal(site.getId(), FrequenceRotation.QUOTIDIEN, orgId);
            log.info("Auto-created pointage code for new site: {} ({})", site.getNom(), site.getId());
        } catch (Exception e) {
            log.warn("Failed to auto-create pointage code for site {}: {}", site.getId(), e.getMessage());
        }

        return site;
    }

    @Transactional
    public Site update(String id, SiteDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Site site = siteRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id));
        site.setNom(dto.nom());
        site.setAdresse(dto.adresse());
        site.setTelephone(dto.telephone());
        site.setActif(dto.actif());
        return siteRepository.save(site);
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Site site = siteRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", id));
        siteRepository.delete(site);
    }
}

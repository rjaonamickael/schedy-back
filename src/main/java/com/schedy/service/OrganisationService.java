package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.OrganisationDto;
import com.schedy.entity.Organisation;
import com.schedy.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final TenantContext tenantContext;
    private final OrganisationCacheStore cacheStore;

    /**
     * Returns only the caller's own organisation (B-M17).
     * In a multi-tenant SaaS, an ADMIN must not see other tenants' organisations.
     */
    @Transactional(readOnly = true)
    public List<Organisation> findAll() {
        String orgId = tenantContext.requireOrganisationId();
        Organisation org = cacheStore.findByIdCached(orgId, organisationRepository);
        return List.of(org);
    }

    @Transactional(readOnly = true)
    public Organisation findById(String id) {
        // B-M17: Ensure caller can only access their own organisation
        String orgId = tenantContext.requireOrganisationId();
        if (!orgId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces refuse");
        }
        return cacheStore.findByIdCached(id, organisationRepository);
    }

    @Transactional
    public Organisation create(OrganisationDto dto) {
        if (organisationRepository.existsByNom(dto.nom())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une organisation avec ce nom existe deja");
        }
        Organisation organisation = Organisation.builder()
                .nom(dto.nom())
                .domaine(dto.domaine())
                .adresse(dto.adresse())
                .telephone(dto.telephone())
                .pays(dto.pays())
                .dateRenouvellementConges(dto.dateRenouvellementConges() != null ? dto.dateRenouvellementConges() : "01-01")
                .build();
        return organisationRepository.save(organisation);
    }

    @Transactional
    public Organisation update(String id, OrganisationDto dto) {
        // findById enforces tenant scope; goes through the service method (tenant check)
        Organisation organisation = findById(id);
        organisation.setNom(dto.nom());
        organisation.setDomaine(dto.domaine());
        organisation.setAdresse(dto.adresse());
        organisation.setTelephone(dto.telephone());
        organisation.setPays(dto.pays());
        if (dto.dateRenouvellementConges() != null) {
            organisation.setDateRenouvellementConges(dto.dateRenouvellementConges());
        }
        Organisation saved = organisationRepository.save(organisation);
        cacheStore.evict(id);
        return saved;
    }

    @Transactional
    public void delete(String id) {
        // B-M17: Tenant scope — findById enforces the caller owns this org
        findById(id);
        organisationRepository.deleteById(id);
        cacheStore.evict(id);
    }

    /**
     * Separate Spring-managed bean so that @Cacheable/@CacheEvict annotations are
     * intercepted by the AOP proxy. The id is passed explicitly so the cache key
     * is never coupled to the request-scoped TenantContext.
     */
    @Component
    @RequiredArgsConstructor
    public static class OrganisationCacheStore {

        @Cacheable(value = "organisations", key = "#id")
        @Transactional(readOnly = true)
        public Organisation findByIdCached(String id, OrganisationRepository repo) {
            return repo.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Organisation non trouvee"));
        }

        @CacheEvict(value = "organisations", key = "#id")
        public void evict(String id) {
            // eviction only — no body needed
        }
    }
}

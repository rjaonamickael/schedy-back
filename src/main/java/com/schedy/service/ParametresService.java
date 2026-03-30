package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.ParametresDto;
import com.schedy.entity.Parametres;
import com.schedy.repository.ParametresRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ParametresService {

    private final ParametresRepository parametresRepository;
    private final TenantContext tenantContext;
    private final ParametresCacheStore cacheStore;

    @Transactional
    public Parametres get() {
        String orgId = tenantContext.requireOrganisationId();
        return cacheStore.getForOrg(orgId, parametresRepository);
    }

    @Transactional
    public Parametres getBySite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return cacheStore.getBySiteForOrg(siteId, orgId, parametresRepository);
    }

    @Transactional
    public Parametres update(ParametresDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Parametres parametres = cacheStore.getForOrg(orgId, parametresRepository);
        applyDto(parametres, dto);
        Parametres saved = parametresRepository.save(parametres);
        cacheStore.evictOrg(orgId);
        return saved;
    }

    @Transactional
    public Parametres updateBySite(String siteId, ParametresDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Parametres parametres = parametresRepository.findBySiteIdAndOrganisationId(siteId, orgId)
                .orElseGet(() -> {
                    Parametres defaults = Parametres.builder()
                            .siteId(siteId)
                            .heureDebut(6)
                            .heureFin(22)
                            .joursActifs(List.of(1, 2, 3, 4, 5))
                            .premierJour(1)
                            .dureeMinAffectation(1.0)
                            .organisationId(orgId)
                            .build();
                    return parametresRepository.save(defaults);
                });
        applyDto(parametres, dto);
        Parametres saved = parametresRepository.save(parametres);
        cacheStore.evictSite(siteId, orgId);
        return saved;
    }

    /** B-17: Shared helper to apply DTO fields onto an existing Parametres entity. */
    private void applyDto(Parametres parametres, ParametresDto dto) {
        parametres.setHeureDebut(dto.heureDebut());
        parametres.setHeureFin(dto.heureFin());
        if (dto.joursActifs() != null) {
            parametres.getJoursActifs().clear();
            parametres.getJoursActifs().addAll(dto.joursActifs());
        }
        parametres.setPremierJour(dto.premierJour());
        parametres.setDureeMinAffectation(dto.dureeMinAffectation());
        parametres.setHeuresMaxSemaine(dto.heuresMaxSemaine());
        parametres.setTaillePolice(dto.taillePolice());
        parametres.setPlanningVue(dto.planningVue());
        parametres.setPlanningGranularite(dto.planningGranularite());
        if (dto.reglesAffectation() != null) {
            parametres.getReglesAffectation().clear();
            parametres.getReglesAffectation().addAll(dto.reglesAffectation());
        }
    }

    /**
     * Separate Spring-managed bean so that @Cacheable/@CacheEvict are intercepted
     * by the AOP proxy. Self-invocation within ParametresService would bypass the
     * proxy and silently skip caching. orgId is passed explicitly so the cache key
     * is never tied to the request-scoped TenantContext.
     */
    @Component
    @RequiredArgsConstructor
    public static class ParametresCacheStore {

        @Cacheable(value = "parametres", key = "#orgId")
        @Transactional(readOnly = true)
        public Parametres getForOrg(String orgId, ParametresRepository repo) {
            return repo.findBySiteIdIsNullAndOrganisationId(orgId)
                    .orElseGet(() -> {
                        try {
                            Parametres defaults = Parametres.builder()
                                    .heureDebut(6)
                                    .heureFin(22)
                                    .joursActifs(List.of(1, 2, 3, 4, 5))
                                    .premierJour(1)
                                    .dureeMinAffectation(1.0)
                                    .organisationId(orgId)
                                    .build();
                            return repo.save(defaults);
                        } catch (DataIntegrityViolationException e) {
                            // Race condition: another request already created the row
                            return repo.findBySiteIdIsNullAndOrganisationId(orgId).orElseThrow();
                        }
                    });
        }

        @Cacheable(value = "parametres", key = "#orgId + ':' + #siteId")
        @Transactional(readOnly = true)
        public Parametres getBySiteForOrg(String siteId, String orgId, ParametresRepository repo) {
            Optional<Parametres> bySite = repo.findBySiteIdAndOrganisationId(siteId, orgId);
            // Fall back to org-level parametres if no site-specific entry exists.
            // We call repo directly (not getForOrg) to avoid a nested @Cacheable call
            // inside a @Cacheable method, which would bypass the proxy again.
            return bySite.orElseGet(() ->
                    repo.findBySiteIdIsNullAndOrganisationId(orgId).orElseGet(() -> {
                        try {
                            Parametres defaults = Parametres.builder()
                                    .heureDebut(6)
                                    .heureFin(22)
                                    .joursActifs(List.of(1, 2, 3, 4, 5))
                                    .premierJour(1)
                                    .dureeMinAffectation(1.0)
                                    .organisationId(orgId)
                                    .build();
                            return repo.save(defaults);
                        } catch (DataIntegrityViolationException e) {
                            return repo.findBySiteIdIsNullAndOrganisationId(orgId).orElseThrow();
                        }
                    })
            );
        }

        @CacheEvict(value = "parametres", allEntries = true)
        public void evictOrg(String orgId) {
            // Evict ALL parametres entries — org-level update may affect site-level fallback cache
        }

        @CacheEvict(value = "parametres", key = "#orgId + ':' + #siteId")
        public void evictSite(String siteId, String orgId) {
            // eviction only — no body needed
        }
    }
}

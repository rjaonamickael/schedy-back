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
            parametres.setJoursActifs(new java.util.ArrayList<>(dto.joursActifs()));
        }
        parametres.setPremierJour(dto.premierJour());
        parametres.setDureeMinAffectation(dto.dureeMinAffectation());
        parametres.setHeuresMaxSemaine(dto.heuresMaxSemaine());
        if (dto.dureeMaxJour() != null) {
            parametres.setDureeMaxJour(dto.dureeMaxJour());
        }
        parametres.setTaillePolice(dto.taillePolice());
        parametres.setPlanningVue(dto.planningVue());
        parametres.setPlanningGranularite(dto.planningGranularite());
        if (dto.reglesAffectation() != null) {
            parametres.setReglesAffectation(new java.util.ArrayList<>(dto.reglesAffectation()));
        }
        if (dto.delaiSignalementAbsenceMinutes() != null) {
            parametres.setDelaiSignalementAbsenceMinutes(dto.delaiSignalementAbsenceMinutes());
        }
        if (dto.seuilAbsenceVsCongeHeures() != null) {
            parametres.setSeuilAbsenceVsCongeHeures(dto.seuilAbsenceVsCongeHeures());
        }

        // Labor law constraints
        if (dto.reposMinEntreShifts() != null) parametres.setReposMinEntreShifts(dto.reposMinEntreShifts());
        if (dto.reposHebdoMin() != null) parametres.setReposHebdoMin(dto.reposHebdoMin());
        if (dto.maxJoursConsecutifs() != null) parametres.setMaxJoursConsecutifs(dto.maxJoursConsecutifs());

        // Pause Layer 1: fixed collective
        if (dto.pauseFixeHeureDebut() != null) parametres.setPauseFixeHeureDebut(dto.pauseFixeHeureDebut());
        if (dto.pauseFixeHeureFin() != null) parametres.setPauseFixeHeureFin(dto.pauseFixeHeureFin());
        if (dto.pauseFixeJours() != null) parametres.setPauseFixeJours(new java.util.ArrayList<>(dto.pauseFixeJours()));

        // Pause Layer 2: tiered rules
        if (dto.pauseAvancee() != null) parametres.setPauseAvancee(dto.pauseAvancee());
        if (dto.pauseSeuilHeures() != null) parametres.setPauseSeuilHeures(dto.pauseSeuilHeures());
        if (dto.pauseDureeMinutes() != null) parametres.setPauseDureeMinutes(dto.pauseDureeMinutes());
        if (dto.pausePayee() != null) parametres.setPausePayee(dto.pausePayee());
        if (dto.reglesPause() != null) {
            // W4: validate no duplicate rules at the same tier (seuilMin + type)
            validateReglesPause(dto.reglesPause());
            parametres.getReglesPause().clear();
            parametres.getReglesPause().addAll(dto.reglesPause());
        }

        // Pause Layer 3: detection window
        if (dto.fenetrePauseMinMinutes() != null) parametres.setFenetrePauseMinMinutes(dto.fenetrePauseMinMinutes());
        if (dto.fenetrePauseMaxMinutes() != null) parametres.setFenetrePauseMaxMinutes(dto.fenetrePauseMaxMinutes());
        if (dto.pauseRenoncementAutorise() != null) parametres.setPauseRenoncementAutorise(dto.pauseRenoncementAutorise());
    }

    /**
     * Validates that no two break rules share the same (seuilMinHeures, type) combination,
     * which would cause double-counting in BreakCalculator. Throws BusinessRuleException if invalid.
     */
    private void validateReglesPause(java.util.List<com.schedy.entity.ReglePause> regles) {
        var seen = new java.util.HashSet<String>();
        for (var r : regles) {
            String key = r.getSeuilMinHeures() + "|" + r.getType();
            if (!seen.add(key)) {
                throw new com.schedy.exception.BusinessRuleException(
                        "Duplicate break rule: two rules of type " + r.getType()
                        + " at the same threshold " + r.getSeuilMinHeures() + "h. "
                        + "Each tier should have at most one rule per type.");
            }
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
        @Transactional
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
        @Transactional
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

        @CacheEvict(value = "parametres", key = "#orgId")
        public void evictOrg(String orgId) {
            // Evicts the org-level cache entry keyed by orgId.
            // Site-level fallback entries (key="#orgId + ':' + siteId") are handled by evictSite.
        }

        @CacheEvict(value = "parametres", key = "#orgId + ':' + #siteId")
        public void evictSite(String siteId, String orgId) {
            // eviction only — no body needed
        }
    }
}

package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.ParametresDto;
import com.schedy.entity.Parametres;
import com.schedy.repository.ParametresRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ParametresService {

    private final ParametresRepository parametresRepository;
    private final TenantContext tenantContext;

    @Transactional
    public Parametres get() {
        String orgId = tenantContext.requireOrganisationId();
        return parametresRepository.findBySiteIdIsNullAndOrganisationId(orgId)
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
                        return parametresRepository.save(defaults);
                    } catch (DataIntegrityViolationException e) {
                        // Race condition: another request created the row — read it
                        return parametresRepository.findBySiteIdIsNullAndOrganisationId(orgId).orElseThrow();
                    }
                });
    }

    @Transactional
    public Parametres getBySite(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        Optional<Parametres> bySite = parametresRepository.findBySiteIdAndOrganisationId(siteId, orgId);
        return bySite.orElseGet(this::get);
    }

    @Transactional
    public Parametres update(ParametresDto dto) {
        Parametres parametres = get();
        applyDto(parametres, dto);
        return parametresRepository.save(parametres);
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
        return parametresRepository.save(parametres);
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
}

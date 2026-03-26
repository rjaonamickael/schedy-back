package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.OrganisationDto;
import com.schedy.entity.Organisation;
import com.schedy.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganisationService {

    private final OrganisationRepository organisationRepository;
    private final TenantContext tenantContext;

    /**
     * Returns only the caller's own organisation (B-M17).
     * In a multi-tenant SaaS, an ADMIN must not see other tenants' organisations.
     */
    @Transactional(readOnly = true)
    public List<Organisation> findAll() {
        String orgId = tenantContext.requireOrganisationId();
        return organisationRepository.findById(orgId).map(List::of).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Organisation findById(String id) {
        // B-M17: Ensure caller can only access their own organisation
        String orgId = tenantContext.requireOrganisationId();
        if (!orgId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acces refuse");
        }
        return organisationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation non trouvee"));
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
                .build();
        return organisationRepository.save(organisation);
    }

    @Transactional
    public Organisation update(String id, OrganisationDto dto) {
        Organisation organisation = findById(id); // findById already enforces tenant scope
        organisation.setNom(dto.nom());
        organisation.setDomaine(dto.domaine());
        organisation.setAdresse(dto.adresse());
        organisation.setTelephone(dto.telephone());
        organisation.setPays(dto.pays());
        return organisationRepository.save(organisation);
    }

    @Transactional
    public void delete(String id) {
        // B-M17: Tenant scope — findById enforces the caller owns this org
        findById(id);
        organisationRepository.deleteById(id);
    }
}

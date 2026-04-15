package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.request.UpdateOrgProfileRequest;
import com.schedy.dto.response.OrgProfileResponse;
import com.schedy.entity.Organisation;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin self-service org profile reads & writes. The org id is taken from
 * the {@link TenantContext} (JWT claim), never from a path parameter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgProfileService {

    private final OrganisationRepository organisationRepository;
    private final TenantContext tenantContext;

    @Transactional(readOnly = true)
    public OrgProfileResponse getCurrentOrgProfile() {
        String orgId = tenantContext.requireOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation introuvable: " + orgId));
        return OrgProfileResponse.from(org);
    }

    @Transactional
    public OrgProfileResponse updateCurrentOrgProfile(UpdateOrgProfileRequest request) {
        String orgId = tenantContext.requireOrganisationId();
        Organisation org = organisationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation introuvable: " + orgId));

        if (request.nom() != null) {
            String trimmed = request.nom().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Le nom de l'organisation ne peut pas etre vide.");
            }
            if (!trimmed.equals(org.getNom())
                    && organisationRepository.existsByNomAndIdNot(trimmed, orgId)) {
                throw new IllegalStateException("Nom deja utilise par une autre organisation.");
            }
            org.setNom(trimmed);
        }
        if (request.domaine() != null) org.setDomaine(emptyToNull(request.domaine()));
        if (request.adresse() != null) org.setAdresse(emptyToNull(request.adresse()));
        if (request.telephone() != null) org.setTelephone(emptyToNull(request.telephone()));
        if (request.pays() != null) org.setPays(emptyToNull(request.pays()));
        if (request.province() != null) org.setProvince(emptyToNull(request.province()));
        if (request.businessNumber() != null) org.setBusinessNumber(emptyToNull(request.businessNumber()));
        if (request.provincialId() != null) org.setProvincialId(emptyToNull(request.provincialId()));
        if (request.nif() != null) org.setNif(emptyToNull(request.nif()));
        if (request.stat() != null) org.setStat(emptyToNull(request.stat()));
        if (request.legalRepresentative() != null) org.setLegalRepresentative(emptyToNull(request.legalRepresentative()));
        if (request.contactEmail() != null) org.setContactEmail(emptyToNull(request.contactEmail()));
        if (request.siret() != null) org.setSiret(emptyToNull(request.siret()));

        Organisation saved = organisationRepository.save(org);
        log.info("Org profile updated org={}", orgId);
        return OrgProfileResponse.from(saved);
    }

    private String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

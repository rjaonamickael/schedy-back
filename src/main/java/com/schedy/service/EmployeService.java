package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.EmployeDto;
import com.schedy.entity.Employe;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.BanqueCongeRepository;
import com.schedy.repository.CreneauAssigneRepository;
import com.schedy.repository.DemandeCongeRepository;
import com.schedy.repository.EmployeRepository;
import com.schedy.repository.PointageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final TenantContext tenantContext;
    private final CreneauAssigneRepository creneauAssigneRepository;
    private final PointageRepository pointageRepository;
    private final DemandeCongeRepository demandeCongeRepository;
    private final BanqueCongeRepository banqueCongeRepository;

    @Transactional(readOnly = true)
    public Page<Employe> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByOrganisationId(orgId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Employe> findAll() {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public Employe findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
    }

    @Transactional(readOnly = true)
    public Optional<Employe> findByPin(String pin) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByPinAndOrganisationId(pin, orgId);
    }

    @Transactional(readOnly = true)
    public List<Employe> findByRole(String role) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByRoleAndOrganisationId(role, orgId);
    }

    @Transactional(readOnly = true)
    public List<Employe> findBySiteId(String siteId) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findBySiteIdsContainingAndOrganisationId(siteId, orgId);
    }

    @Transactional(readOnly = true)
    public Page<Employe> findBySiteId(String siteId, Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findBySiteIdsContainingAndOrganisationId(siteId, orgId, pageable);
    }

    @Transactional
    public Employe create(EmployeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = Employe.builder()
                .nom(dto.nom())
                .role(dto.role())
                .telephone(dto.telephone())
                .email(dto.email())
                .dateNaissance(dto.dateNaissance())
                .dateEmbauche(dto.dateEmbauche())
                .pin(dto.pin())
                .organisationId(orgId)
                .disponibilites(dto.disponibilites() != null ? dto.disponibilites() : Collections.emptyList())
                .siteIds(dto.siteIds() != null ? dto.siteIds() : Collections.emptyList())
                .build();
        return employeRepository.save(employe);
    }

    @Transactional
    public Employe update(String id, EmployeDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
        employe.setNom(dto.nom());
        employe.setRole(dto.role());
        employe.setTelephone(dto.telephone());
        employe.setEmail(dto.email());
        employe.setDateNaissance(dto.dateNaissance());
        employe.setDateEmbauche(dto.dateEmbauche());
        employe.setPin(dto.pin());
        if (dto.disponibilites() != null) {
            employe.getDisponibilites().clear();
            employe.getDisponibilites().addAll(dto.disponibilites());
        }
        if (dto.siteIds() != null) {
            employe.getSiteIds().clear();
            employe.getSiteIds().addAll(dto.siteIds());
        }
        return employeRepository.save(employe);
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Employe employe = employeRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
        creneauAssigneRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        pointageRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        demandeCongeRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        banqueCongeRepository.deleteByEmployeIdAndOrganisationId(id, orgId);
        employeRepository.delete(employe);
    }
}

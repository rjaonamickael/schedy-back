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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
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
    private final PasswordEncoder passwordEncoder;

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

    /**
     * Find employee by raw PIN. Uses SHA-256 index for O(1) lookup,
     * then verifies with bcrypt for security.
     */
    @Transactional(readOnly = true)
    public Optional<Employe> findByPin(String rawPin) {
        String orgId = tenantContext.requireOrganisationId();
        String hash = sha256(rawPin);
        return employeRepository.findByPinHashAndOrganisationId(hash, orgId)
                .filter(emp -> emp.getPin() != null && passwordEncoder.matches(rawPin, emp.getPin()));
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
                .pin(dto.pin() != null ? passwordEncoder.encode(dto.pin()) : null)
                .pinHash(dto.pin() != null ? sha256(dto.pin()) : null)
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
        if (dto.pin() != null && !dto.pin().isBlank()) {
            employe.setPin(passwordEncoder.encode(dto.pin()));
            employe.setPinHash(sha256(dto.pin()));
        }
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

    /**
     * Verify a raw PIN against the hashed PIN stored for the employee.
     */
    public boolean verifyPin(String employeId, String rawPin) {
        String orgId = tenantContext.requireOrganisationId();
        return employeRepository.findByIdAndOrganisationId(employeId, orgId)
                .map(emp -> emp.getPin() != null && passwordEncoder.matches(rawPin, emp.getPin()))
                .orElse(false);
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

    /**
     * Compute SHA-256 hex digest of a string. Used as a fast-lookup index
     * for PIN matching before the expensive bcrypt verification.
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

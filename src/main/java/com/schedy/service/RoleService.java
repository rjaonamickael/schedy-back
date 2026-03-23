package com.schedy.service;

import com.schedy.config.TenantContext;
import com.schedy.dto.RoleDto;
import com.schedy.entity.Role;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final TenantContext tenantContext;

    public List<Role> findAllOrdered() {
        String orgId = tenantContext.requireOrganisationId();
        return roleRepository.findByOrganisationIdOrderByImportanceAsc(orgId);
    }

    public Page<Role> findAll(Pageable pageable) {
        String orgId = tenantContext.requireOrganisationId();
        return roleRepository.findByOrganisationId(orgId, pageable);
    }

    public Role findById(String id) {
        String orgId = tenantContext.requireOrganisationId();
        return roleRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    @Transactional
    public Role create(RoleDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        if (roleRepository.findByNomAndOrganisationId(dto.nom(), orgId).isPresent()) {
            throw new IllegalStateException("Un role avec ce nom existe deja : " + dto.nom());
        }
        Role role = Role.builder()
                .nom(dto.nom())
                .importance(dto.importance())
                .couleur(dto.couleur())
                .organisationId(orgId)
                .build();
        return roleRepository.save(role);
    }

    @Transactional
    public Role update(String id, RoleDto dto) {
        String orgId = tenantContext.requireOrganisationId();
        Role role = roleRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
        roleRepository.findByNomAndOrganisationId(dto.nom(), orgId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalStateException("Un role avec ce nom existe deja : " + dto.nom());
                });
        role.setNom(dto.nom());
        role.setImportance(dto.importance());
        role.setCouleur(dto.couleur());
        return roleRepository.save(role);
    }

    @Transactional
    public void delete(String id) {
        String orgId = tenantContext.requireOrganisationId();
        Role role = roleRepository.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
        roleRepository.delete(role);
    }

    @Transactional
    public List<Role> reorder(List<RoleDto> roles) {
        String orgId = tenantContext.requireOrganisationId();
        for (int i = 0; i < roles.size(); i++) {
            RoleDto dto = roles.get(i);
            if (dto.id() != null) {
                Role role = roleRepository.findByIdAndOrganisationId(dto.id(), orgId)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", dto.id()));
                role.setImportance(i);
                roleRepository.save(role);
            }
        }
        return findAllOrdered();
    }
}

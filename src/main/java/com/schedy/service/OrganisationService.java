package com.schedy.service;

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

    public List<Organisation> findAll() {
        return organisationRepository.findAll();
    }

    public Organisation findById(String id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation non trouvée"));
    }

    @Transactional
    public Organisation create(OrganisationDto dto) {
        if (organisationRepository.existsByNom(dto.nom())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une organisation avec ce nom existe déjà");
        }
        Organisation organisation = Organisation.builder()
                .nom(dto.nom())
                .domaine(dto.domaine())
                .adresse(dto.adresse())
                .telephone(dto.telephone())
                .build();
        return organisationRepository.save(organisation);
    }

    @Transactional
    public Organisation update(String id, OrganisationDto dto) {
        Organisation organisation = findById(id);
        organisation.setNom(dto.nom());
        organisation.setDomaine(dto.domaine());
        organisation.setAdresse(dto.adresse());
        organisation.setTelephone(dto.telephone());
        return organisationRepository.save(organisation);
    }

    @Transactional
    public void delete(String id) {
        if (!organisationRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation non trouvée");
        }
        organisationRepository.deleteById(id);
    }
}

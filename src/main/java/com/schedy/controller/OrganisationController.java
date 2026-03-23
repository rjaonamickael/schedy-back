package com.schedy.controller;

import com.schedy.dto.OrganisationDto;
import com.schedy.entity.Organisation;
import com.schedy.service.OrganisationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
public class OrganisationController {

    private final OrganisationService organisationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Organisation> getAll() {
        return organisationService.findAll();
    }

    @GetMapping("/{id}")
    public Organisation getById(@PathVariable String id) {
        return organisationService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Organisation> create(@Valid @RequestBody OrganisationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Organisation update(@PathVariable String id, @Valid @RequestBody OrganisationDto dto) {
        return organisationService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String id) {
        organisationService.delete(id);
    }
}

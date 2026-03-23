package com.schedy.controller;

import com.schedy.dto.OrganisationDto;
import com.schedy.dto.response.OrganisationResponse;
import com.schedy.service.OrganisationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organisations")
@RequiredArgsConstructor
public class OrganisationController {

    private final OrganisationService organisationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrganisationResponse>> getAll() {
        return ResponseEntity.ok(organisationService.findAll().stream().map(OrganisationResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganisationResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(OrganisationResponse.from(organisationService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganisationResponse> create(@Valid @RequestBody OrganisationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganisationResponse.from(organisationService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganisationResponse> update(@PathVariable String id, @Valid @RequestBody OrganisationDto dto) {
        return ResponseEntity.ok(OrganisationResponse.from(organisationService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        organisationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

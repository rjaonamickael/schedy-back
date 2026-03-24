package com.schedy.controller;

import com.schedy.dto.SiteDto;
import com.schedy.dto.response.SiteResponse;
import com.schedy.service.SiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    public ResponseEntity<Page<SiteResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(siteService.findAll(pageable).map(SiteResponse::from));
    }

    @GetMapping("/all")
    public ResponseEntity<List<SiteResponse>> getAllActifs() {
        return ResponseEntity.ok(siteService.findAllActifs().stream().map(SiteResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SiteResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(SiteResponse.from(siteService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SiteResponse> create(@Valid @RequestBody SiteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SiteResponse.from(siteService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SiteResponse> update(@PathVariable String id, @Valid @RequestBody SiteDto dto) {
        return ResponseEntity.ok(SiteResponse.from(siteService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        siteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

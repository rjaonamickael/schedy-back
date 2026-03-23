package com.schedy.controller;

import com.schedy.dto.SiteDto;
import com.schedy.entity.Site;
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
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    public ResponseEntity<Page<Site>> getAll(Pageable pageable) {
        return ResponseEntity.ok(siteService.findAll(pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Site>> getAllActifs() {
        return ResponseEntity.ok(siteService.findAllActifs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Site> getById(@PathVariable String id) {
        return ResponseEntity.ok(siteService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Site> create(@Valid @RequestBody SiteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Site> update(@PathVariable String id, @Valid @RequestBody SiteDto dto) {
        return ResponseEntity.ok(siteService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        siteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

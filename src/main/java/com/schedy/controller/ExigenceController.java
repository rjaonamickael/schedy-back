package com.schedy.controller;

import com.schedy.dto.ExigenceDto;
import com.schedy.entity.Exigence;
import com.schedy.service.ExigenceService;
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
@RequestMapping("/api/exigences")
@RequiredArgsConstructor
public class ExigenceController {

    private final ExigenceService exigenceService;

    @GetMapping
    public ResponseEntity<Page<Exigence>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(exigenceService.findBySiteId(siteId, pageable));
        }
        return ResponseEntity.ok(exigenceService.findAll(pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Exigence>> findAll(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(exigenceService.findBySiteId(siteId));
        }
        return ResponseEntity.ok(exigenceService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Exigence> findById(@PathVariable String id) {
        return ResponseEntity.ok(exigenceService.findById(id));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<Exigence>> findByRole(@PathVariable String role,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(exigenceService.findByRoleAndSiteId(role, siteId));
        }
        return ResponseEntity.ok(exigenceService.findByRole(role));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Exigence> create(@Valid @RequestBody ExigenceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exigenceService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Exigence> update(@PathVariable String id, @Valid @RequestBody ExigenceDto dto) {
        return ResponseEntity.ok(exigenceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        exigenceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

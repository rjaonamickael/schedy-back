package com.schedy.controller;

import com.schedy.dto.ExigenceDto;
import com.schedy.dto.response.ExigenceResponse;
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
@RequestMapping("/api/v1/exigences")
@RequiredArgsConstructor
public class ExigenceController {

    private final ExigenceService exigenceService;

    @GetMapping
    public ResponseEntity<Page<ExigenceResponse>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(exigenceService.findBySiteId(siteId, pageable).map(ExigenceResponse::from));
        }
        return ResponseEntity.ok(exigenceService.findAll(pageable).map(ExigenceResponse::from));
    }

    @GetMapping("/all")
    public ResponseEntity<List<ExigenceResponse>> findAll(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(exigenceService.findBySiteId(siteId).stream().map(ExigenceResponse::from).toList());
        }
        return ResponseEntity.ok(exigenceService.findAll().stream().map(ExigenceResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExigenceResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(ExigenceResponse.from(exigenceService.findById(id)));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<ExigenceResponse>> findByRole(@PathVariable String role,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(exigenceService.findByRoleAndSiteId(role, siteId).stream().map(ExigenceResponse::from).toList());
        }
        return ResponseEntity.ok(exigenceService.findByRole(role).stream().map(ExigenceResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ExigenceResponse> create(@Valid @RequestBody ExigenceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ExigenceResponse.from(exigenceService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ExigenceResponse> update(@PathVariable String id, @Valid @RequestBody ExigenceDto dto) {
        return ResponseEntity.ok(ExigenceResponse.from(exigenceService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        exigenceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

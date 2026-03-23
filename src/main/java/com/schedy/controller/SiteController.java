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
    public Page<Site> getAll(Pageable pageable) {
        return siteService.findAll(pageable);
    }

    @GetMapping("/all")
    public List<Site> getAllActifs() {
        return siteService.findAllActifs();
    }

    @GetMapping("/{id}")
    public Site getById(@PathVariable String id) {
        return siteService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Site> create(@Valid @RequestBody SiteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Site update(@PathVariable String id, @Valid @RequestBody SiteDto dto) {
        return siteService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String id) {
        siteService.delete(id);
    }
}

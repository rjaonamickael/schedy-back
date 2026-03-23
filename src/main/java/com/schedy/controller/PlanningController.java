package com.schedy.controller;

import com.schedy.dto.CreneauAssigneDto;
import com.schedy.entity.CreneauAssigne;
import com.schedy.service.PlanningService;
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
@RequestMapping("/api/creneaux")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;

    @GetMapping
    public ResponseEntity<Page<CreneauAssigne>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findBySite(siteId, pageable));
        }
        return ResponseEntity.ok(planningService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreneauAssigne> findById(@PathVariable String id) {
        return ResponseEntity.ok(planningService.findById(id));
    }

    @GetMapping("/semaine/{semaine}")
    public ResponseEntity<List<CreneauAssigne>> findBySemaine(@PathVariable String semaine,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findBySemaineAndSite(semaine, siteId));
        }
        return ResponseEntity.ok(planningService.findBySemaine(semaine));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<CreneauAssigne>> findByEmployeId(@PathVariable String employeId,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findByEmployeIdAndSite(employeId, siteId));
        }
        return ResponseEntity.ok(planningService.findByEmployeId(employeId));
    }

    @GetMapping("/employe/{employeId}/semaine/{semaine}")
    public ResponseEntity<List<CreneauAssigne>> findByEmployeIdAndSemaine(
            @PathVariable String employeId, @PathVariable String semaine,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findByEmployeIdAndSemaineAndSite(employeId, semaine, siteId));
        }
        return ResponseEntity.ok(planningService.findByEmployeIdAndSemaine(employeId, semaine));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CreneauAssigne> create(@Valid @RequestBody CreneauAssigneDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.create(dto));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CreneauAssigne>> createBatch(@Valid @RequestBody List<CreneauAssigneDto> dtos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.createBatch(dtos));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CreneauAssigne> update(@PathVariable String id, @Valid @RequestBody CreneauAssigneDto dto) {
        return ResponseEntity.ok(planningService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        planningService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/semaine/{semaine}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteBySemaine(@PathVariable String semaine,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            planningService.deleteBySemaineAndSite(semaine, siteId);
        } else {
            planningService.deleteBySemaine(semaine);
        }
        return ResponseEntity.noContent().build();
    }
}

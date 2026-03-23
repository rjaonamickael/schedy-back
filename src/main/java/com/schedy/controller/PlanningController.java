package com.schedy.controller;

import com.schedy.dto.CreneauAssigneDto;
import com.schedy.dto.request.AutoAffectationRequest;
import com.schedy.dto.response.AutoAffectationResponse;
import com.schedy.dto.response.CreneauAssigneResponse;
import com.schedy.service.AutoAffectationService;
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
@RequestMapping("/api/v1/creneaux")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;
    private final AutoAffectationService autoAffectationService;

    @GetMapping
    public ResponseEntity<Page<CreneauAssigneResponse>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findBySite(siteId, pageable).map(CreneauAssigneResponse::from));
        }
        return ResponseEntity.ok(planningService.findAll(pageable).map(CreneauAssigneResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreneauAssigneResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(CreneauAssigneResponse.from(planningService.findById(id)));
    }

    @GetMapping("/semaine/{semaine}")
    public ResponseEntity<List<CreneauAssigneResponse>> findBySemaine(@PathVariable String semaine,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findBySemaineAndSite(semaine, siteId).stream().map(CreneauAssigneResponse::from).toList());
        }
        return ResponseEntity.ok(planningService.findBySemaine(semaine).stream().map(CreneauAssigneResponse::from).toList());
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<CreneauAssigneResponse>> findByEmployeId(@PathVariable String employeId,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findByEmployeIdAndSite(employeId, siteId).stream().map(CreneauAssigneResponse::from).toList());
        }
        return ResponseEntity.ok(planningService.findByEmployeId(employeId).stream().map(CreneauAssigneResponse::from).toList());
    }

    @GetMapping("/employe/{employeId}/semaine/{semaine}")
    public ResponseEntity<List<CreneauAssigneResponse>> findByEmployeIdAndSemaine(
            @PathVariable String employeId, @PathVariable String semaine,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(planningService.findByEmployeIdAndSemaineAndSite(employeId, semaine, siteId).stream().map(CreneauAssigneResponse::from).toList());
        }
        return ResponseEntity.ok(planningService.findByEmployeIdAndSemaine(employeId, semaine).stream().map(CreneauAssigneResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CreneauAssigneResponse> create(@Valid @RequestBody CreneauAssigneDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CreneauAssigneResponse.from(planningService.create(dto)));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CreneauAssigneResponse>> createBatch(@Valid @RequestBody List<CreneauAssigneDto> dtos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                planningService.createBatch(dtos).stream().map(CreneauAssigneResponse::from).toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CreneauAssigneResponse> update(@PathVariable String id, @Valid @RequestBody CreneauAssigneDto dto) {
        return ResponseEntity.ok(CreneauAssigneResponse.from(planningService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        planningService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auto-affecter")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AutoAffectationResponse> autoAffecter(@Valid @RequestBody AutoAffectationRequest request) {
        var result = autoAffectationService.autoAffecter(request.semaine(), request.siteId());
        return ResponseEntity.ok(new AutoAffectationResponse(result.totalAffectes(), result.creneaux()));
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

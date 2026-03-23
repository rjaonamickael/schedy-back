package com.schedy.controller;

import com.schedy.dto.request.PointerRequest;
import com.schedy.dto.PointageDto;
import com.schedy.entity.Pointage;
import com.schedy.service.PointageService;
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
@RequestMapping("/api/pointages")
@RequiredArgsConstructor
public class PointageController {

    private final PointageService pointageService;

    @GetMapping
    public ResponseEntity<Page<Pointage>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findBySiteId(siteId, pageable));
        }
        return ResponseEntity.ok(pointageService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pointage> findById(@PathVariable String id) {
        return ResponseEntity.ok(pointageService.findById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<Pointage>> findByEmployeId(@PathVariable String employeId,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findByEmployeIdAndSiteId(employeId, siteId));
        }
        return ResponseEntity.ok(pointageService.findByEmployeId(employeId));
    }

    @GetMapping("/aujourd-hui")
    public ResponseEntity<List<Pointage>> findTodayAll(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findTodayAllBySite(siteId));
        }
        return ResponseEntity.ok(pointageService.findTodayAll());
    }

    @GetMapping("/aujourd-hui/employe/{employeId}")
    public ResponseEntity<List<Pointage>> findTodayByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(pointageService.findTodayByEmployeId(employeId));
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Pointage>> findAnomalies(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findAnomaliesBySite(siteId));
        }
        return ResponseEntity.ok(pointageService.findAnomalies());
    }

    @PostMapping("/pointer")
    public ResponseEntity<Pointage> pointer(@Valid @RequestBody PointerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pointageService.pointer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Pointage> update(@PathVariable String id, @Valid @RequestBody PointageDto dto) {
        return ResponseEntity.ok(pointageService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        pointageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

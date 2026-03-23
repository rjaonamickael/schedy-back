package com.schedy.controller;

import com.schedy.dto.request.PointerRequest;
import com.schedy.dto.PointageDto;
import com.schedy.dto.response.PointageResponse;
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
@RequestMapping("/api/v1/pointages")
@RequiredArgsConstructor
public class PointageController {

    private final PointageService pointageService;

    @GetMapping
    public ResponseEntity<Page<PointageResponse>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findBySiteId(siteId, pageable).map(PointageResponse::from));
        }
        return ResponseEntity.ok(pointageService.findAll(pageable).map(PointageResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PointageResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(PointageResponse.from(pointageService.findById(id)));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<PointageResponse>> findByEmployeId(@PathVariable String employeId,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findByEmployeIdAndSiteId(employeId, siteId).stream().map(PointageResponse::from).toList());
        }
        return ResponseEntity.ok(pointageService.findByEmployeId(employeId).stream().map(PointageResponse::from).toList());
    }

    @GetMapping("/aujourd-hui")
    public ResponseEntity<List<PointageResponse>> findTodayAll(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findTodayAllBySite(siteId).stream().map(PointageResponse::from).toList());
        }
        return ResponseEntity.ok(pointageService.findTodayAll().stream().map(PointageResponse::from).toList());
    }

    @GetMapping("/aujourd-hui/employe/{employeId}")
    public ResponseEntity<List<PointageResponse>> findTodayByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(pointageService.findTodayByEmployeId(employeId).stream().map(PointageResponse::from).toList());
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<PointageResponse>> findAnomalies(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(pointageService.findAnomaliesBySite(siteId).stream().map(PointageResponse::from).toList());
        }
        return ResponseEntity.ok(pointageService.findAnomalies().stream().map(PointageResponse::from).toList());
    }

    @PostMapping("/pointer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageResponse> pointer(@Valid @RequestBody PointerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PointageResponse.from(pointageService.pointer(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageResponse> update(@PathVariable String id, @Valid @RequestBody PointageDto dto) {
        return ResponseEntity.ok(PointageResponse.from(pointageService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        pointageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

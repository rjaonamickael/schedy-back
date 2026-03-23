package com.schedy.controller;

import com.schedy.dto.EmployeDto;
import com.schedy.entity.Employe;
import com.schedy.service.EmployeService;
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
@RequestMapping("/api/employes")
@RequiredArgsConstructor
public class EmployeController {

    private final EmployeService employeService;

    @GetMapping
    public ResponseEntity<Page<Employe>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(employeService.findBySiteId(siteId, pageable));
        }
        return ResponseEntity.ok(employeService.findAll(pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Employe>> findAll(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(employeService.findBySiteId(siteId));
        }
        return ResponseEntity.ok(employeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employe> findById(@PathVariable String id) {
        return ResponseEntity.ok(employeService.findById(id));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<Employe>> findByRole(@PathVariable String role) {
        return ResponseEntity.ok(employeService.findByRole(role));
    }

    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<Employe>> findBySiteId(@PathVariable String siteId) {
        return ResponseEntity.ok(employeService.findBySiteId(siteId));
    }

    @GetMapping("/pin/{pin}")
    public ResponseEntity<Employe> findByPin(@PathVariable String pin) {
        return employeService.findByPin(pin)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Employe> create(@Valid @RequestBody EmployeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Employe> update(@PathVariable String id, @Valid @RequestBody EmployeDto dto) {
        return ResponseEntity.ok(employeService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        employeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

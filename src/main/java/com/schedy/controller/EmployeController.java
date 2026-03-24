package com.schedy.controller;

import com.schedy.dto.EmployeDto;
import com.schedy.dto.response.EmployeResponse;
import com.schedy.service.EmployeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employes")
@RequiredArgsConstructor
public class EmployeController {

    private final EmployeService employeService;

    @GetMapping
    public ResponseEntity<Page<EmployeResponse>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(employeService.findBySiteId(siteId, pageable).map(EmployeResponse::from));
        }
        return ResponseEntity.ok(employeService.findAll(pageable).map(EmployeResponse::from));
    }

    @GetMapping("/all")
    public ResponseEntity<List<EmployeResponse>> findAll(
            @RequestParam(value = "siteId", required = false) String siteId) {
        Page<EmployeResponse> page;
        if (siteId != null) {
            page = employeService.findBySiteId(siteId, PageRequest.of(0, 1000)).map(EmployeResponse::from);
        } else {
            page = employeService.findAll(PageRequest.of(0, 1000)).map(EmployeResponse::from);
        }
        return ResponseEntity.ok(page.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(EmployeResponse.from(employeService.findById(id)));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<EmployeResponse>> findByRole(@PathVariable String role) {
        return ResponseEntity.ok(employeService.findByRole(role).stream().map(EmployeResponse::from).toList());
    }

    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<EmployeResponse>> findBySiteId(@PathVariable String siteId) {
        return ResponseEntity.ok(employeService.findBySiteId(siteId).stream().map(EmployeResponse::from).toList());
    }

    @PostMapping("/find-by-pin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeResponse> findByPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (pin == null || pin.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return employeService.findByPin(pin)
                .map(EmployeResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeResponse> create(@Valid @RequestBody EmployeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(EmployeResponse.from(employeService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeResponse> update(@PathVariable String id, @Valid @RequestBody EmployeDto dto) {
        return ResponseEntity.ok(EmployeResponse.from(employeService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        employeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

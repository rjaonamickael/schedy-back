package com.schedy.controller;

import com.schedy.dto.RoleDto;
import com.schedy.dto.response.RoleResponse;
import com.schedy.service.RoleService;
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
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<Page<RoleResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(roleService.findAll(pageable).map(RoleResponse::from));
    }

    @GetMapping("/all")
    public ResponseEntity<List<RoleResponse>> findAllOrdered() {
        return ResponseEntity.ok(roleService.findAllOrdered().stream().map(RoleResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(RoleResponse.from(roleService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RoleResponse.from(roleService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<RoleResponse> update(@PathVariable String id, @Valid @RequestBody RoleDto dto) {
        return ResponseEntity.ok(RoleResponse.from(roleService.update(id, dto)));
    }

    @PatchMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<RoleResponse>> reorder(@Valid @RequestBody List<@Valid RoleDto> roles) {
        return ResponseEntity.ok(roleService.reorder(roles).stream().map(RoleResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

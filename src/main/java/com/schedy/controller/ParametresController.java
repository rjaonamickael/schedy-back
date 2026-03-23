package com.schedy.controller;

import com.schedy.dto.ParametresDto;
import com.schedy.entity.Parametres;
import com.schedy.service.ParametresService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parametres")
@RequiredArgsConstructor
public class ParametresController {

    private final ParametresService parametresService;

    @GetMapping
    public ResponseEntity<Parametres> get(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(parametresService.getBySite(siteId));
        }
        return ResponseEntity.ok(parametresService.get());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Parametres> update(@RequestBody ParametresDto dto,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(parametresService.updateBySite(siteId, dto));
        }
        return ResponseEntity.ok(parametresService.update(dto));
    }
}

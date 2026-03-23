package com.schedy.controller;

import com.schedy.dto.ParametresDto;
import com.schedy.dto.response.ParametresResponse;
import com.schedy.service.ParametresService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/parametres")
@RequiredArgsConstructor
public class ParametresController {

    private final ParametresService parametresService;

    @GetMapping
    public ResponseEntity<ParametresResponse> get(
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(ParametresResponse.from(parametresService.getBySite(siteId)));
        }
        return ResponseEntity.ok(ParametresResponse.from(parametresService.get()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ParametresResponse> update(@RequestBody ParametresDto dto,
            @RequestParam(value = "siteId", required = false) String siteId) {
        if (siteId != null) {
            return ResponseEntity.ok(ParametresResponse.from(parametresService.updateBySite(siteId, dto)));
        }
        return ResponseEntity.ok(ParametresResponse.from(parametresService.update(dto)));
    }
}

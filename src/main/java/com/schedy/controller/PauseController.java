package com.schedy.controller;

import com.schedy.dto.request.ContesterPauseRequest;
import com.schedy.dto.response.PauseResponse;
import com.schedy.service.PauseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pauses")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PauseController {

    private final PauseService pauseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<PauseResponse>> findByDate(
            @RequestParam String date,
            @RequestParam(required = false) String siteId) {
        return ResponseEntity.ok(
                pauseService.findByDate(date, siteId).stream().map(PauseResponse::from).toList()
        );
    }

    @PutMapping("/{id}/confirmer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PauseResponse> confirmer(@PathVariable String id) {
        return ResponseEntity.ok(PauseResponse.from(pauseService.confirmer(id)));
    }

    @PutMapping("/{id}/contester")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PauseResponse> contester(@PathVariable String id,
                                                    @RequestBody ContesterPauseRequest request) {
        return ResponseEntity.ok(PauseResponse.from(pauseService.contester(id, request.motif())));
    }
}

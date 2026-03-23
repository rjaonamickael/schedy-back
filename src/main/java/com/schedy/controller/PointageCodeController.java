package com.schedy.controller;

import com.schedy.dto.PointageCodeDto;
import com.schedy.dto.request.PointageCodeConfigRequest;
import com.schedy.dto.request.ValidatePointageCodeRequest;
import com.schedy.dto.response.PointageResponse;
import com.schedy.entity.PointageCode.FrequenceRotation;
import com.schedy.dto.request.PointerRequest;
import com.schedy.service.PointageCodeService;
import com.schedy.service.PointageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pointage-codes")
@RequiredArgsConstructor
public class PointageCodeController {

    private final PointageCodeService pointageCodeService;
    private final PointageService pointageService;

    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageCodeDto> getActiveForSite(@PathVariable("siteId") String siteId) {
        PointageCodeDto dto = pointageCodeService.getActiveForSite(siteId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/configure")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageCodeDto> configure(@Valid @RequestBody PointageCodeConfigRequest request) {
        FrequenceRotation frequence = FrequenceRotation.valueOf(request.frequence());
        PointageCodeDto dto = pointageCodeService.getOrCreateForSite(request.siteId(), frequence);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/regenerate/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageCodeDto> regenerate(
            @PathVariable("siteId") String siteId,
            @RequestParam(value = "frequence", required = false) String frequence) {
        FrequenceRotation freq = frequence != null ? FrequenceRotation.valueOf(frequence) : null;
        PointageCodeDto dto = pointageCodeService.regenerateNow(siteId, freq);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Public endpoint for kiosk displays - no auth required.
     * Returns the active code + PIN for a site so the kiosk can display them.
     */
    @GetMapping("/kiosk/{siteId}")
    public ResponseEntity<PointageCodeDto> getForKiosk(@PathVariable("siteId") String siteId) {
        PointageCodeDto dto = pointageCodeService.getActiveForSitePublic(siteId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * Public endpoint for validating a code/PIN and creating a pointage.
     * No auth required - the code itself is the authentication.
     * The organisationId is resolved from the PointageCode.
     */
    @PostMapping("/validate")
    public ResponseEntity<PointageResponse> validate(@Valid @RequestBody ValidatePointageCodeRequest request) {
        // 1. Resolve organisationId from the code before validating
        String organisationId = pointageCodeService.resolveOrganisationIdFromCode(request.code());

        // 2. Validate the code/PIN and get the siteId
        String siteId = pointageCodeService.validateCode(request.code());

        // 3. Create a pointage for the employee at the resolved site
        PointerRequest pointerRequest = new PointerRequest(request.employeId(), siteId, "qr");
        PointageResponse pointage = PointageResponse.from(pointageService.pointerFromKiosk(pointerRequest, organisationId));

        return ResponseEntity.status(HttpStatus.CREATED).body(pointage);
    }
}

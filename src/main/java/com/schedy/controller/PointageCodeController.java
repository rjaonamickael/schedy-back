package com.schedy.controller;

import com.schedy.dto.PointageCodeDto;
import com.schedy.dto.request.PointageCodeConfigRequest;
import com.schedy.dto.request.ValidatePointageCodeRequest;
import com.schedy.dto.response.KioskPointageCodeResponse;
import com.schedy.dto.response.PointageResponse;
import com.schedy.exception.BusinessRuleException;
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
        FrequenceRotation frequence = parseFrequence(request.frequence());
        PointageCodeDto dto = pointageCodeService.getOrCreateForSite(request.siteId(), frequence);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/regenerate/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageCodeDto> regenerate(
            @PathVariable("siteId") String siteId,
            @RequestParam(value = "frequence", required = false) String frequence) {
        FrequenceRotation freq = frequence != null ? parseFrequence(frequence) : null;
        PointageCodeDto dto = pointageCodeService.regenerateNow(siteId, freq);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Public endpoint for kiosk displays - no auth required.
     * Returns the active code for a site (PIN intentionally excluded for security).
     */
    @GetMapping("/kiosk/{siteId}")
    public ResponseEntity<KioskPointageCodeResponse> getForKiosk(@PathVariable("siteId") String siteId) {
        KioskPointageCodeResponse dto = pointageCodeService.getActiveForSitePublic(siteId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * Public endpoint for validating a code/PIN and creating a pointage.
     * No auth required - the code itself is the authentication.
     * The organisationId is resolved from the PointageCode in a single DB read.
     */
    @PostMapping("/validate")
    public ResponseEntity<PointageResponse> validate(@Valid @RequestBody ValidatePointageCodeRequest request) {
        // 1. Single atomic call: validate code + resolve siteId + organisationId (B-M18)
        PointageCodeService.CodeValidationResult result = pointageCodeService.validateAndResolve(request.code());

        // 2. Verify that the employeId belongs to the resolved organisation (B-H17)
        pointageCodeService.verifyEmployeBelongsToOrganisation(request.employeId(), result.organisationId());

        // 3. Create a pointage for the employee at the resolved site
        PointerRequest pointerRequest = new PointerRequest(request.employeId(), result.siteId(), "qr");
        PointageResponse pointage = PointageResponse.from(pointageService.pointerFromKiosk(pointerRequest, result.organisationId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(pointage);
    }

    private FrequenceRotation parseFrequence(String value) {
        try {
            return FrequenceRotation.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Valeur invalide pour frequence: " + value);
        }
    }
}

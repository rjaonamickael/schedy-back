package com.schedy.controller;

import com.schedy.dto.PointageCodeDto;
import com.schedy.dto.request.PointageCodeConfigRequest;
import com.schedy.dto.request.ValidateKioskAdminRequest;
import com.schedy.dto.request.ValidatePointageCodeRequest;
import com.schedy.dto.response.KioskPointageCodeResponse;
import com.schedy.dto.response.PointageResponse;
import com.schedy.entity.PointageCode.UniteRotation;
import com.schedy.exception.BusinessRuleException;
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

    /**
     * Configure (or idempotently return) a pointage code for a site.
     * Body: { "siteId": "...", "rotationValeur": 1, "rotationUnite": "JOURS" }
     */
    @PostMapping("/configure")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageCodeDto> configure(@Valid @RequestBody PointageCodeConfigRequest request) {
        UniteRotation unite = parseUniteRotation(request.rotationUnite());
        PointageCodeDto dto = pointageCodeService.getOrCreateForSite(
                request.siteId(), request.rotationValeur(), unite);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Force an immediate rotation for a site, optionally changing the rotation schedule.
     * Query params rotationValeur + rotationUnite are optional; omitting them carries over
     * the existing values from the current active code.
     */
    @PostMapping("/regenerate/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PointageCodeDto> regenerate(
            @PathVariable("siteId") String siteId,
            @RequestParam(value = "rotationValeur", required = false) Integer rotationValeur,
            @RequestParam(value = "rotationUnite",  required = false) String  rotationUniteStr) {
        UniteRotation unite = rotationUniteStr != null ? parseUniteRotation(rotationUniteStr) : null;
        PointageCodeDto dto = pointageCodeService.regenerateNow(siteId, rotationValeur, unite);
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
        PointageResponse pointage = PointageResponse.from(
                pointageService.pointerFromKiosk(pointerRequest, result.organisationId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(pointage);
    }

    /**
     * Public endpoint for kiosk admin code validation.
     * Used to exit kiosk mode — the admin code is validated server-side
     * so it never appears in the frontend JS bundle.
     */
    @PostMapping("/kiosk/admin/validate")
    public ResponseEntity<java.util.Map<String, Boolean>> validateKioskAdmin(
            @Valid @RequestBody ValidateKioskAdminRequest request) {
        boolean valid = pointageCodeService.validateKioskAdminCode(request.code());
        return ResponseEntity.ok(java.util.Map.of("valid", valid));
    }

    /**
     * Public kiosk endpoint: clock in/out via employee PIN.
     * Resolves the employee by PIN + siteId, creates the pointage, returns
     * employee info + pointage for the kiosk feedback screen.
     * No auth required — the PIN is the authentication.
     */
    @PostMapping("/kiosk/pin-clock")
    public ResponseEntity<java.util.Map<String, Object>> kioskPinClock(
            @RequestBody java.util.Map<String, String> body) {
        String pin    = body.get("pin");
        String siteId = body.get("siteId");
        if (pin == null || pin.isBlank() || siteId == null || siteId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // 1. Find employee by PIN across all orgs for this site
        var employe = pointageCodeService.findEmployeByPinAndSite(pin, siteId);
        if (employe == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", "PIN invalide"));
        }

        // 2. Create pointage
        PointerRequest pointerRequest = new PointerRequest(employe.getId(), siteId, "pin");
        var pointage = PointageResponse.from(
                pointageService.pointerFromKiosk(pointerRequest, employe.getOrganisationId()));

        // 3. Return employee info + pointage for feedback
        return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of(
                "employeId",  employe.getId(),
                "employeNom", employe.getNom() != null ? employe.getNom() : "",
                "pointage",   pointage
        ));
    }

    // ---- Helpers ----

    private UniteRotation parseUniteRotation(String value) {
        try {
            return UniteRotation.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(
                    "Valeur invalide pour rotationUnite: " + value +
                    ". Valeurs acceptees: MINUTES, HEURES, JOURS, SEMAINES, MOIS");
        }
    }
}

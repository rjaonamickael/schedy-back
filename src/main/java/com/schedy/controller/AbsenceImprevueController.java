package com.schedy.controller;

import com.schedy.dto.request.AbsenceImprevueRequest;
import com.schedy.dto.request.DecisionAbsenceRequest;
import com.schedy.dto.request.ReassignerCreneauRequest;
import com.schedy.dto.response.AbsenceImprevueResponse;
import com.schedy.service.AbsenceImprevueService;
import com.schedy.service.ReplacementService;
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
@RequestMapping("/api/v1/absences-imprevues")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AbsenceImprevueController {

    private final AbsenceImprevueService absenceService;
    private final ReplacementService replacementService;

    /**
     * POST /api/v1/absences-imprevues
     * EMPLOYEE : signale SA PROPRE absence → statut SIGNALEE → notification managers par email
     * MANAGER/ADMIN : signale l'absence d'un employé → statut VALIDEE (auto-validé)
     */
    @PostMapping
    public ResponseEntity<AbsenceImprevueResponse> signalerAbsence(
            @Valid @RequestBody AbsenceImprevueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AbsenceImprevueResponse.from(absenceService.signalerAbsence(request)));
    }

    /**
     * PUT /api/v1/absences-imprevues/{id}/valider
     * Manager accepte l'absence signalée par l'employé.
     */
    @PutMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AbsenceImprevueResponse> valider(
            @PathVariable String id,
            @RequestBody(required = false) DecisionAbsenceRequest request) {
        return ResponseEntity.ok(AbsenceImprevueResponse.from(absenceService.valider(id, request)));
    }

    /**
     * PUT /api/v1/absences-imprevues/{id}/refuser
     * Manager refuse l'absence. noteRefus obligatoire.
     */
    @PutMapping("/{id}/refuser")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AbsenceImprevueResponse> refuser(
            @PathVariable String id,
            @RequestBody DecisionAbsenceRequest request) {
        return ResponseEntity.ok(AbsenceImprevueResponse.from(absenceService.refuser(id, request)));
    }

    /**
     * PUT /api/v1/absences-imprevues/{id}/annuler
     * EMPLOYEE : annule si encore SIGNALEE.
     * MANAGER : annule si SIGNALEE ou VALIDEE.
     */
    @PutMapping("/{id}/annuler")
    public ResponseEntity<AbsenceImprevueResponse> annuler(@PathVariable String id) {
        return ResponseEntity.ok(AbsenceImprevueResponse.from(absenceService.annuler(id)));
    }

    /**
     * POST /api/v1/absences-imprevues/replace
     * Réassigner un créneau impacté à un remplaçant.
     */
    @PostMapping("/replace")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AbsenceImprevueResponse> reassignerCreneau(
            @Valid @RequestBody ReassignerCreneauRequest request) {
        return ResponseEntity.ok(AbsenceImprevueResponse.from(absenceService.reassignerCreneau(request)));
    }

    /**
     * PUT /api/v1/absences-imprevues/{id}/traiter
     * Marquer une absence comme traitée (tous les créneaux gérés).
     */
    @PutMapping("/{id}/traiter")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AbsenceImprevueResponse> marquerTraitee(@PathVariable String id) {
        return ResponseEntity.ok(AbsenceImprevueResponse.from(absenceService.marquerTraitee(id)));
    }

    /**
     * GET /api/v1/absences-imprevues/mes-alertes
     * EMPLOYEE : ses propres absences signalées + statut.
     */
    @GetMapping("/mes-alertes")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<List<AbsenceImprevueResponse>> getMesAlertes() {
        return ResponseEntity.ok(absenceService.findMesAlertes().stream()
                .map(AbsenceImprevueResponse::from)
                .toList());
    }

    /**
     * GET /api/v1/absences-imprevues/en-attente
     * MANAGER : absences SIGNALEE en attente de validation.
     */
    @GetMapping("/en-attente")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AbsenceImprevueResponse>> getEnAttente() {
        return ResponseEntity.ok(absenceService.findEnAttente().stream()
                .map(AbsenceImprevueResponse::from)
                .toList());
    }

    /**
     * GET /api/v1/absences-imprevues/actives
     * MANAGER : toutes les absences actives (SIGNALEE + VALIDEE + EN_COURS).
     */
    @GetMapping("/actives")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AbsenceImprevueResponse>> getActives() {
        return ResponseEntity.ok(absenceService.findActives().stream()
                .map(AbsenceImprevueResponse::from)
                .toList());
    }

    /**
     * GET /api/v1/absences-imprevues/replacements?creneauId=
     * Remplaçants suggérés pour un créneau, triés par score.
     */
    @GetMapping("/replacements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<ReplacementService.RemplacantDto>> getReplacements(
            @RequestParam String creneauId) {
        return ResponseEntity.ok(replacementService.findReplacements(creneauId));
    }

    /**
     * GET /api/v1/absences-imprevues/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AbsenceImprevueResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(AbsenceImprevueResponse.from(absenceService.findById(id)));
    }

    /**
     * GET /api/v1/absences-imprevues
     * Historique paginé.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<AbsenceImprevueResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(absenceService.findAll(pageable).map(AbsenceImprevueResponse::from));
    }

    /**
     * GET /api/v1/absences-imprevues/seuil
     * Retourne le seuil configurable (en heures) pour distinguer absence imprévue vs congé.
     */
    @GetMapping("/seuil")
    public ResponseEntity<Integer> getSeuil(@RequestParam(required = false) String siteId) {
        return ResponseEntity.ok(absenceService.getSeuilAbsenceVsConge(siteId));
    }
}

package com.schedy.controller;

import com.schedy.dto.*;
import com.schedy.dto.response.*;
import com.schedy.service.CongeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/conges")
@RequiredArgsConstructor
public class CongeController {

    private final CongeService congeService;

    // ---- Types de congé ----

    @GetMapping("/types")
    public ResponseEntity<Page<TypeCongeResponse>> findAllTypes(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllTypes(pageable).map(TypeCongeResponse::from));
    }

    @GetMapping("/types/all")
    public ResponseEntity<List<TypeCongeResponse>> findAllTypes() {
        return ResponseEntity.ok(congeService.findAllTypes().stream().map(TypeCongeResponse::from).toList());
    }

    @GetMapping("/types/{id}")
    public ResponseEntity<TypeCongeResponse> findTypeById(@PathVariable String id) {
        return ResponseEntity.ok(TypeCongeResponse.from(congeService.findTypeById(id)));
    }

    @PostMapping("/types")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TypeCongeResponse> createType(@Valid @RequestBody TypeCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TypeCongeResponse.from(congeService.createType(dto)));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TypeCongeResponse> updateType(@PathVariable String id, @Valid @RequestBody TypeCongeDto dto) {
        return ResponseEntity.ok(TypeCongeResponse.from(congeService.updateType(id, dto)));
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteType(@PathVariable String id) {
        congeService.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Banques de congé ----

    @GetMapping("/banques")
    public ResponseEntity<Page<BanqueCongeResponse>> findAllBanques(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllBanques(pageable).map(BanqueCongeResponse::from));
    }

    @GetMapping("/banques/all")
    public ResponseEntity<List<BanqueCongeResponse>> findAllBanques() {
        return ResponseEntity.ok(congeService.findAllBanques().stream().map(BanqueCongeResponse::from).toList());
    }

    @GetMapping("/banques/{id}")
    public ResponseEntity<BanqueCongeResponse> findBanqueById(@PathVariable String id) {
        return ResponseEntity.ok(BanqueCongeResponse.from(congeService.findBanqueById(id)));
    }

    @GetMapping("/banques/employe/{employeId}")
    public ResponseEntity<List<BanqueCongeResponse>> findBanquesByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(congeService.findBanquesByEmployeId(employeId).stream().map(BanqueCongeResponse::from).toList());
    }

    @PostMapping("/banques")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BanqueCongeResponse> createBanque(@Valid @RequestBody BanqueCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BanqueCongeResponse.from(congeService.createBanque(dto)));
    }

    @PutMapping("/banques/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BanqueCongeResponse> updateBanque(@PathVariable String id, @Valid @RequestBody BanqueCongeDto dto) {
        return ResponseEntity.ok(BanqueCongeResponse.from(congeService.updateBanque(id, dto)));
    }

    @DeleteMapping("/banques/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBanque(@PathVariable String id) {
        congeService.deleteBanque(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Demandes de congé ----

    @GetMapping("/demandes")
    public ResponseEntity<Page<DemandeCongeResponse>> findAllDemandes(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllDemandes(pageable).map(DemandeCongeResponse::from));
    }

    @GetMapping("/demandes/all")
    public ResponseEntity<List<DemandeCongeResponse>> findAllDemandes() {
        return ResponseEntity.ok(congeService.findAllDemandes().stream().map(DemandeCongeResponse::from).toList());
    }

    @GetMapping("/demandes/{id}")
    public ResponseEntity<DemandeCongeResponse> findDemandeById(@PathVariable String id) {
        return ResponseEntity.ok(DemandeCongeResponse.from(congeService.findDemandeById(id)));
    }

    @GetMapping("/demandes/employe/{employeId}")
    public ResponseEntity<List<DemandeCongeResponse>> findDemandesByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(congeService.findDemandesByEmployeId(employeId).stream().map(DemandeCongeResponse::from).toList());
    }

    @PostMapping("/demandes")
    public ResponseEntity<DemandeCongeResponse> createDemande(@Valid @RequestBody DemandeCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DemandeCongeResponse.from(congeService.createDemande(dto)));
    }

    @PutMapping("/demandes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DemandeCongeResponse> updateDemande(@PathVariable String id, @Valid @RequestBody DemandeCongeDto dto) {
        return ResponseEntity.ok(DemandeCongeResponse.from(congeService.updateDemande(id, dto)));
    }

    @PutMapping("/demandes/{id}/approuver")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DemandeCongeResponse> approveDemande(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("noteApprobation") : null;
        return ResponseEntity.ok(DemandeCongeResponse.from(congeService.approveDemande(id, note)));
    }

    @PutMapping("/demandes/{id}/refuser")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DemandeCongeResponse> refuseDemande(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("noteApprobation") : null;
        return ResponseEntity.ok(DemandeCongeResponse.from(congeService.refuseDemande(id, note)));
    }

    @DeleteMapping("/demandes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteDemande(@PathVariable String id) {
        congeService.deleteDemande(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Jours fériés ----

    @GetMapping("/feries")
    public ResponseEntity<Page<JourFerieResponse>> findAllFeries(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllFeries(pageable).map(JourFerieResponse::from));
    }

    @GetMapping("/feries/all")
    public ResponseEntity<List<JourFerieResponse>> findAllFeries() {
        return ResponseEntity.ok(congeService.findAllFeries().stream().map(JourFerieResponse::from).toList());
    }

    @GetMapping("/feries/{id}")
    public ResponseEntity<JourFerieResponse> findFerieById(@PathVariable String id) {
        return ResponseEntity.ok(JourFerieResponse.from(congeService.findFerieById(id)));
    }

    @PostMapping("/feries")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<JourFerieResponse> createFerie(@Valid @RequestBody JourFerieDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(JourFerieResponse.from(congeService.createFerie(dto)));
    }

    @PutMapping("/feries/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<JourFerieResponse> updateFerie(@PathVariable String id, @Valid @RequestBody JourFerieDto dto) {
        return ResponseEntity.ok(JourFerieResponse.from(congeService.updateFerie(id, dto)));
    }

    @DeleteMapping("/feries/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFerie(@PathVariable String id) {
        congeService.deleteFerie(id);
        return ResponseEntity.noContent().build();
    }
}

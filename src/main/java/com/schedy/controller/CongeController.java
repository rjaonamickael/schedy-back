package com.schedy.controller;

import com.schedy.dto.*;
import com.schedy.entity.*;
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
@RequestMapping("/api/conges")
@RequiredArgsConstructor
public class CongeController {

    private final CongeService congeService;

    // ---- Types de congé ----

    @GetMapping("/types")
    public ResponseEntity<Page<TypeConge>> findAllTypes(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllTypes(pageable));
    }

    @GetMapping("/types/all")
    public ResponseEntity<List<TypeConge>> findAllTypes() {
        return ResponseEntity.ok(congeService.findAllTypes());
    }

    @GetMapping("/types/{id}")
    public ResponseEntity<TypeConge> findTypeById(@PathVariable String id) {
        return ResponseEntity.ok(congeService.findTypeById(id));
    }

    @PostMapping("/types")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TypeConge> createType(@Valid @RequestBody TypeCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(congeService.createType(dto));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TypeConge> updateType(@PathVariable String id, @Valid @RequestBody TypeCongeDto dto) {
        return ResponseEntity.ok(congeService.updateType(id, dto));
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteType(@PathVariable String id) {
        congeService.deleteType(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Banques de congé ----

    @GetMapping("/banques")
    public ResponseEntity<Page<BanqueConge>> findAllBanques(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllBanques(pageable));
    }

    @GetMapping("/banques/all")
    public ResponseEntity<List<BanqueConge>> findAllBanques() {
        return ResponseEntity.ok(congeService.findAllBanques());
    }

    @GetMapping("/banques/{id}")
    public ResponseEntity<BanqueConge> findBanqueById(@PathVariable String id) {
        return ResponseEntity.ok(congeService.findBanqueById(id));
    }

    @GetMapping("/banques/employe/{employeId}")
    public ResponseEntity<List<BanqueConge>> findBanquesByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(congeService.findBanquesByEmployeId(employeId));
    }

    @PostMapping("/banques")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BanqueConge> createBanque(@Valid @RequestBody BanqueCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(congeService.createBanque(dto));
    }

    @PutMapping("/banques/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<BanqueConge> updateBanque(@PathVariable String id, @Valid @RequestBody BanqueCongeDto dto) {
        return ResponseEntity.ok(congeService.updateBanque(id, dto));
    }

    @DeleteMapping("/banques/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBanque(@PathVariable String id) {
        congeService.deleteBanque(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Demandes de congé ----

    @GetMapping("/demandes")
    public ResponseEntity<Page<DemandeConge>> findAllDemandes(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllDemandes(pageable));
    }

    @GetMapping("/demandes/all")
    public ResponseEntity<List<DemandeConge>> findAllDemandes() {
        return ResponseEntity.ok(congeService.findAllDemandes());
    }

    @GetMapping("/demandes/{id}")
    public ResponseEntity<DemandeConge> findDemandeById(@PathVariable String id) {
        return ResponseEntity.ok(congeService.findDemandeById(id));
    }

    @GetMapping("/demandes/employe/{employeId}")
    public ResponseEntity<List<DemandeConge>> findDemandesByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(congeService.findDemandesByEmployeId(employeId));
    }

    @PostMapping("/demandes")
    public ResponseEntity<DemandeConge> createDemande(@Valid @RequestBody DemandeCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(congeService.createDemande(dto));
    }

    @PutMapping("/demandes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DemandeConge> updateDemande(@PathVariable String id, @Valid @RequestBody DemandeCongeDto dto) {
        return ResponseEntity.ok(congeService.updateDemande(id, dto));
    }

    @PutMapping("/demandes/{id}/approuver")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DemandeConge> approveDemande(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("noteApprobation") : null;
        return ResponseEntity.ok(congeService.approveDemande(id, note));
    }

    @PutMapping("/demandes/{id}/refuser")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<DemandeConge> refuseDemande(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("noteApprobation") : null;
        return ResponseEntity.ok(congeService.refuseDemande(id, note));
    }

    @DeleteMapping("/demandes/{id}")
    public ResponseEntity<Void> deleteDemande(@PathVariable String id) {
        congeService.deleteDemande(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Jours fériés ----

    @GetMapping("/feries")
    public ResponseEntity<Page<JourFerie>> findAllFeries(Pageable pageable) {
        return ResponseEntity.ok(congeService.findAllFeries(pageable));
    }

    @GetMapping("/feries/all")
    public ResponseEntity<List<JourFerie>> findAllFeries() {
        return ResponseEntity.ok(congeService.findAllFeries());
    }

    @GetMapping("/feries/{id}")
    public ResponseEntity<JourFerie> findFerieById(@PathVariable String id) {
        return ResponseEntity.ok(congeService.findFerieById(id));
    }

    @PostMapping("/feries")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<JourFerie> createFerie(@Valid @RequestBody JourFerieDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(congeService.createFerie(dto));
    }

    @PutMapping("/feries/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<JourFerie> updateFerie(@PathVariable String id, @Valid @RequestBody JourFerieDto dto) {
        return ResponseEntity.ok(congeService.updateFerie(id, dto));
    }

    @DeleteMapping("/feries/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFerie(@PathVariable String id) {
        congeService.deleteFerie(id);
        return ResponseEntity.noContent().build();
    }
}

package com.schedy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedy.config.JwtAuthFilter;
import com.schedy.dto.DemandeCongeDto;
import com.schedy.dto.TypeCongeDto;
import com.schedy.entity.DemandeConge;
import com.schedy.entity.StatutDemande;
import com.schedy.entity.TypeConge;
import com.schedy.entity.TypeLimite;
import com.schedy.entity.UniteConge;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.service.CongeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-05 / Sprint 10 : {@code @WebMvcTest} slice for {@link CongeController}.
 *
 * <p>Exercises the three sub-resources exposed under {@code /api/v1/conges} :
 * {@code /types}, {@code /banques}, {@code /demandes}. Focuses on the
 * approval / refusal workflow which is the most state-sensitive part of the
 * API and the only one that can raise {@link BusinessRuleException} (quota
 * dépassé, demande déjà approuvée…).</p>
 */
@WebMvcTest(CongeController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CongeController @WebMvcTest")
class CongeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CongeService congeService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    private static final String DEMANDE_ID = "dem-1";
    private static final String TYPE_ID = "typ-1";

    // ── Fixtures ─────────────────────────────────────────────────────────

    private DemandeCongeDto validDemandeDto() {
        return new DemandeCongeDto(
                DEMANDE_ID, "emp-1", TYPE_ID,
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 22),
                null, null, 16.0,
                "en_attente", "Vacances", null, "org-1");
    }

    private DemandeConge sampleDemande() {
        return DemandeConge.builder()
                .id(DEMANDE_ID)
                .employeId("emp-1")
                .typeCongeId(TYPE_ID)
                .dateDebut(LocalDate.of(2026, 4, 20))
                .dateFin(LocalDate.of(2026, 4, 22))
                .duree(16.0)
                .statut(StatutDemande.en_attente)
                .motif("Vacances")
                .organisationId("org-1")
                .build();
    }

    private DemandeConge approvedDemande() {
        DemandeConge d = sampleDemande();
        d.setStatut(StatutDemande.approuve);
        d.setNoteApprobation("OK");
        return d;
    }

    private TypeCongeDto validTypeDto() {
        return new TypeCongeDto(
                TYPE_ID, "Vacances", true, "heures",
                "#10B981", "ENVELOPPE_ANNUELLE", 200.0,
                null, null, false,
                null, null, "org-1",
                null); // V39 : genresEligibles (open to all)
    }

    private TypeConge sampleType() {
        return TypeConge.builder()
                .id(TYPE_ID)
                .nom("Vacances")
                .paye(true)
                .unite(UniteConge.heures)
                .couleur("#10B981")
                .typeLimite(TypeLimite.ENVELOPPE_ANNUELLE)
                .quotaAnnuel(200.0)
                .autoriserDepassement(false)
                .organisationId("org-1")
                .build();
    }

    // ── GET /demandes ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /demandes returns 200 with paged demandes")
    void findAllDemandes_returns200() throws Exception {
        Page<DemandeConge> page = new PageImpl<>(List.of(sampleDemande()));
        when(congeService.findAllDemandes(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/conges/demandes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(DEMANDE_ID))
                .andExpect(jsonPath("$.content[0].statut").value("en_attente"));
    }

    // ── POST /demandes ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /demandes returns 201 with created demande")
    void createDemande_returns201() throws Exception {
        when(congeService.createDemande(any(DemandeCongeDto.class))).thenReturn(sampleDemande());

        mockMvc.perform(post("/api/v1/conges/demandes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDemandeDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DEMANDE_ID))
                .andExpect(jsonPath("$.employeId").value("emp-1"));
    }

    @Test
    @DisplayName("POST /demandes returns 400 when employeId is blank (no service call)")
    void createDemande_returns400OnBlankEmploye() throws Exception {
        DemandeCongeDto invalid = new DemandeCongeDto(
                null, "", TYPE_ID,
                LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 22),
                null, null, 16.0, "en_attente", null, null, "org-1");

        mockMvc.perform(post("/api/v1/conges/demandes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.employeId").exists());

        verify(congeService, never()).createDemande(any());
    }

    @Test
    @DisplayName("POST /demandes returns 422 when service raises BusinessRuleException (quota depasse)")
    void createDemande_returns422OnQuotaOverflow() throws Exception {
        when(congeService.createDemande(any(DemandeCongeDto.class)))
                .thenThrow(new BusinessRuleException("Quota de conge depasse"));

        mockMvc.perform(post("/api/v1/conges/demandes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDemandeDto())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Quota de conge depasse"));
    }

    // ── PUT /demandes/{id}/approuver + /refuser ─────────────────────────

    @Test
    @DisplayName("PUT /demandes/{id}/approuver returns 200 with approved demande")
    void approveDemande_returns200() throws Exception {
        when(congeService.approveDemande(eq(DEMANDE_ID), any())).thenReturn(approvedDemande());

        mockMvc.perform(put("/api/v1/conges/demandes/{id}/approuver", DEMANDE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteApprobation\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("approuve"))
                .andExpect(jsonPath("$.noteApprobation").value("OK"));
    }

    @Test
    @DisplayName("PUT /demandes/{id}/approuver returns 404 when demande does not exist")
    void approveDemande_returns404WhenNotFound() throws Exception {
        when(congeService.approveDemande(eq(DEMANDE_ID), any()))
                .thenThrow(new ResourceNotFoundException("Demande de conge", DEMANDE_ID));

        mockMvc.perform(put("/api/v1/conges/demandes/{id}/approuver", DEMANDE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /demandes/{id}/refuser returns 200 with refused demande")
    void refuseDemande_returns200() throws Exception {
        DemandeConge refused = sampleDemande();
        refused.setStatut(StatutDemande.refuse);
        refused.setNoteApprobation("Manque de personnel");
        when(congeService.refuseDemande(eq(DEMANDE_ID), any())).thenReturn(refused);

        mockMvc.perform(put("/api/v1/conges/demandes/{id}/refuser", DEMANDE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteApprobation\":\"Manque de personnel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("refuse"))
                .andExpect(jsonPath("$.noteApprobation").value("Manque de personnel"));
    }

    @Test
    @DisplayName("PUT /demandes/{id}/approuver returns 422 when demande was already approved")
    void approveDemande_returns422OnAlreadyApproved() throws Exception {
        when(congeService.approveDemande(eq(DEMANDE_ID), any()))
                .thenThrow(new BusinessRuleException("Cette demande a deja ete traitee"));

        mockMvc.perform(put("/api/v1/conges/demandes/{id}/approuver", DEMANDE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Cette demande a deja ete traitee"));
    }

    // ── DELETE /demandes/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /demandes/{id} returns 204 No Content")
    void deleteDemande_returns204() throws Exception {
        doNothing().when(congeService).deleteDemande(DEMANDE_ID);

        mockMvc.perform(delete("/api/v1/conges/demandes/{id}", DEMANDE_ID))
                .andExpect(status().isNoContent());

        verify(congeService).deleteDemande(DEMANDE_ID);
    }

    @Test
    @DisplayName("DELETE /demandes/{id} returns 404 when service throws ResourceNotFoundException")
    void deleteDemande_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Demande de conge", DEMANDE_ID))
                .when(congeService).deleteDemande(DEMANDE_ID);

        mockMvc.perform(delete("/api/v1/conges/demandes/{id}", DEMANDE_ID))
                .andExpect(status().isNotFound());
    }

    // ── /types CRUD smoke ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /types/{id} returns 200")
    void findTypeById_returns200() throws Exception {
        when(congeService.findTypeById(TYPE_ID)).thenReturn(sampleType());

        mockMvc.perform(get("/api/v1/conges/types/{id}", TYPE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TYPE_ID))
                .andExpect(jsonPath("$.nom").value("Vacances"))
                .andExpect(jsonPath("$.paye").value(true))
                .andExpect(jsonPath("$.unite").value("heures"));
    }

    @Test
    @DisplayName("POST /types returns 201 with created type")
    void createType_returns201() throws Exception {
        when(congeService.createType(any(TypeCongeDto.class))).thenReturn(sampleType());

        mockMvc.perform(post("/api/v1/conges/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validTypeDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TYPE_ID));
    }

    @Test
    @DisplayName("POST /types returns 400 when nom is blank")
    void createType_returns400OnBlankNom() throws Exception {
        TypeCongeDto invalid = new TypeCongeDto(
                null, "", true, "heures",
                "#10B981", "ENVELOPPE_ANNUELLE", 200.0,
                null, null, false,
                null, null, "org-1",
                null); // V39

        mockMvc.perform(post("/api/v1/conges/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.nom").exists());

        verify(congeService, never()).createType(any());
    }

    @Test
    @DisplayName("DELETE /types/{id} returns 204")
    void deleteType_returns204() throws Exception {
        doNothing().when(congeService).deleteType(TYPE_ID);

        mockMvc.perform(delete("/api/v1/conges/types/{id}", TYPE_ID))
                .andExpect(status().isNoContent());

        verify(congeService).deleteType(TYPE_ID);
    }

    @Test
    @DisplayName("DELETE /types/{id} returns 422 when type is still referenced by a demande")
    void deleteType_returns422WhenReferenced() throws Exception {
        doThrow(new BusinessRuleException("Ce type est encore utilise"))
                .when(congeService).deleteType(TYPE_ID);

        mockMvc.perform(delete("/api/v1/conges/types/{id}", TYPE_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Ce type est encore utilise"));
    }
}

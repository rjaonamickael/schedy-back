package com.schedy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedy.config.JwtAuthFilter;
import com.schedy.dto.PointageDto;
import com.schedy.dto.request.PointageManuelRequest;
import com.schedy.dto.request.PointerRequest;
import com.schedy.entity.MethodePointage;
import com.schedy.entity.Pointage;
import com.schedy.entity.StatutPointage;
import com.schedy.entity.TypePointage;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.service.PointageService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
 * BE-05 / Sprint 10 : {@code @WebMvcTest} slice for {@link PointageController}.
 *
 * <p>Covers the two write flows : self-service {@code /pointer} (pin / web / QR)
 * and admin override {@code /manuel}. The {@code /anomalies} read path is
 * important for the manager dashboard, so it also gets a smoke test.</p>
 */
@WebMvcTest(PointageController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PointageController @WebMvcTest")
class PointageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PointageService pointageService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    private static final String POINTAGE_ID = "pt-1";
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 4, 13, 9, 0, 0, 0, ZoneOffset.UTC);

    // ── Fixtures ─────────────────────────────────────────────────────────

    private Pointage samplePointage() {
        return Pointage.builder()
                .id(POINTAGE_ID)
                .employeId("emp-1")
                .type(TypePointage.entree)
                .horodatage(NOW)
                .methode(MethodePointage.web)
                .statut(StatutPointage.valide)
                .siteId("site-1")
                .organisationId("org-1")
                .build();
    }

    private Pointage anomalyPointage() {
        return Pointage.builder()
                .id("pt-2")
                .employeId("emp-1")
                .type(TypePointage.entree)
                .horodatage(NOW)
                .methode(MethodePointage.web)
                .statut(StatutPointage.anomalie)
                .anomalie("Retard de 15 minutes")
                .siteId("site-1")
                .organisationId("org-1")
                .build();
    }

    private PointerRequest validPointerRequest() {
        return new PointerRequest("emp-1", "site-1", "web");
    }

    private PointageManuelRequest validManuelRequest() {
        return new PointageManuelRequest("emp-1", "site-1", "manuel", "entree", NOW);
    }

    // ── POST /pointer ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /pointer returns 201 with created pointage")
    void pointer_returns201() throws Exception {
        when(pointageService.pointer(any(PointerRequest.class))).thenReturn(samplePointage());

        mockMvc.perform(post("/api/v1/pointages/pointer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPointerRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(POINTAGE_ID))
                .andExpect(jsonPath("$.employeId").value("emp-1"))
                .andExpect(jsonPath("$.type").value("entree"))
                .andExpect(jsonPath("$.methode").value("web"));
    }

    @Test
    @DisplayName("POST /pointer returns 400 when employeId is blank (no service call)")
    void pointer_returns400OnBlankEmploye() throws Exception {
        PointerRequest invalid = new PointerRequest("", "site-1", "web");

        mockMvc.perform(post("/api/v1/pointages/pointer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.employeId").exists());

        verify(pointageService, never()).pointer(any());
    }

    @Test
    @DisplayName("POST /pointer returns 422 when service raises BusinessRuleException (deja pointe)")
    void pointer_returns422OnAlreadyClocked() throws Exception {
        when(pointageService.pointer(any(PointerRequest.class)))
                .thenThrow(new BusinessRuleException("L'employe a deja pointe son entree"));

        mockMvc.perform(post("/api/v1/pointages/pointer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPointerRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("L'employe a deja pointe son entree"));
    }

    // ── POST /manuel ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /manuel returns 201 with admin-created pointage")
    void pointerManuel_returns201() throws Exception {
        when(pointageService.pointerManuel(any(PointageManuelRequest.class))).thenReturn(samplePointage());

        mockMvc.perform(post("/api/v1/pointages/manuel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validManuelRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(POINTAGE_ID));
    }

    @Test
    @DisplayName("POST /manuel returns 400 when horodatage is null")
    void pointerManuel_returns400OnNullHorodatage() throws Exception {
        PointageManuelRequest invalid = new PointageManuelRequest(
                "emp-1", "site-1", "manuel", "entree", null);

        mockMvc.perform(post("/api/v1/pointages/manuel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.horodatage").exists());

        verify(pointageService, never()).pointerManuel(any());
    }

    @Test
    @DisplayName("POST /manuel returns 400 when methode is blank")
    void pointerManuel_returns400OnBlankMethode() throws Exception {
        PointageManuelRequest invalid = new PointageManuelRequest(
                "emp-1", "site-1", "", "entree", NOW);

        mockMvc.perform(post("/api/v1/pointages/manuel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.methode").exists());

        verify(pointageService, never()).pointerManuel(any());
    }

    // ── PUT /{id} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /{id} returns 200 with updated pointage")
    void update_returns200() throws Exception {
        Pointage corrected = samplePointage();
        corrected.setStatut(StatutPointage.corrige);
        when(pointageService.update(eq(POINTAGE_ID), any(PointageDto.class))).thenReturn(corrected);

        PointageDto dto = new PointageDto(
                POINTAGE_ID, "emp-1", "entree", NOW, "web", "corrige",
                null, "site-1", "org-1");

        mockMvc.perform(put("/api/v1/pointages/{id}", POINTAGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("corrige"));
    }

    @Test
    @DisplayName("PUT /{id} returns 400 when type is blank")
    void update_returns400OnBlankType() throws Exception {
        PointageDto invalid = new PointageDto(
                POINTAGE_ID, "emp-1", "", NOW, "web", "valide",
                null, "site-1", "org-1");

        mockMvc.perform(put("/api/v1/pointages/{id}", POINTAGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.type").exists());

        verify(pointageService, never()).update(any(), any());
    }

    // ── DELETE /{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /{id} returns 204")
    void delete_returns204() throws Exception {
        doNothing().when(pointageService).delete(POINTAGE_ID);

        mockMvc.perform(delete("/api/v1/pointages/{id}", POINTAGE_ID))
                .andExpect(status().isNoContent());

        verify(pointageService).delete(POINTAGE_ID);
    }

    @Test
    @DisplayName("DELETE /{id} returns 404 when service throws ResourceNotFoundException")
    void delete_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Pointage", POINTAGE_ID))
                .when(pointageService).delete(POINTAGE_ID);

        mockMvc.perform(delete("/api/v1/pointages/{id}", POINTAGE_ID))
                .andExpect(status().isNotFound());
    }

    // ── GET read paths ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id} returns 200 with pointage")
    void findById_returns200() throws Exception {
        when(pointageService.findById(POINTAGE_ID)).thenReturn(samplePointage());

        mockMvc.perform(get("/api/v1/pointages/{id}", POINTAGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POINTAGE_ID))
                .andExpect(jsonPath("$.siteId").value("site-1"));
    }

    @Test
    @DisplayName("GET /{id} returns 404 when not found")
    void findById_returns404() throws Exception {
        when(pointageService.findById(POINTAGE_ID))
                .thenThrow(new ResourceNotFoundException("Pointage", POINTAGE_ID));

        mockMvc.perform(get("/api/v1/pointages/{id}", POINTAGE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /anomalies returns 200 with list of anomalies")
    void findAnomalies_returns200() throws Exception {
        when(pointageService.findAnomalies()).thenReturn(List.of(anomalyPointage()));

        mockMvc.perform(get("/api/v1/pointages/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pt-2"))
                .andExpect(jsonPath("$[0].statut").value("anomalie"))
                .andExpect(jsonPath("$[0].anomalie").value("Retard de 15 minutes"));
    }

    @Test
    @DisplayName("GET / returns 200 with paged pointages")
    void findAll_returns200() throws Exception {
        Page<Pointage> page = new PageImpl<>(List.of(samplePointage()));
        when(pointageService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/pointages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(POINTAGE_ID));
    }
}

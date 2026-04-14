package com.schedy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedy.config.JwtAuthFilter;
import com.schedy.dto.CreneauAssigneDto;
import com.schedy.entity.CreneauAssigne;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.service.AutoAffectationService;
import com.schedy.service.PlanningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
 * BE-05 / Sprint 9 : first {@code @WebMvcTest} controller test in the project.
 *
 * <p>Validates the full HTTP request/response cycle for the most safety-critical
 * controller : {@link PlanningController}. The {@code PUT /api/v1/creneaux/&#123;id&#125;}
 * route is the entry point used by the frontend drag-and-drop and was the v33 BE-04
 * blocker. This spec exercises :</p>
 *
 * <ol>
 *   <li>Happy path 200 with a valid payload</li>
 *   <li>Sprint 7 BE-04 validation : 422 when {@link PlanningService#update} throws
 *       {@link BusinessRuleException} (overlap, conge approuve, exigence, etc.)</li>
 *   <li>404 mapping when {@link ResourceNotFoundException} is thrown</li>
 *   <li>400 mapping on DTO validation failure (Jakarta Validation, no service call)</li>
 *   <li>POST 201, DELETE 204, GET 200 smoke coverage</li>
 * </ol>
 *
 * <h2>Setup notes</h2>
 * <ul>
 *   <li>{@code @AutoConfigureMockMvc(addFilters = false)} : the Spring Security filter
 *       chain (including {@link JwtAuthFilter}) is bypassed. Authentication and
 *       authorisation (the {@code @PreAuthorize("hasAnyRole(...)")} class-level and
 *       method-level guards) are NOT exercised here — they belong to a future
 *       integration test slice. This test focuses on routing + validation + exception
 *       mapping, which is where BE-04 lives.</li>
 *   <li>{@link MockitoBean} (Spring Framework 6.2+, Spring Boot 3.4) is used instead of
 *       the deprecated {@code @MockBean}. JwtAuthFilter is mocked because the slice
 *       still wires it as a bean even with filters disabled.</li>
 *   <li>{@link com.schedy.exception.GlobalExceptionHandler} is auto-loaded by the
 *       slice (it is a {@code @RestControllerAdvice}), so 422/404/400 responses are
 *       built exactly the same way as in production.</li>
 * </ul>
 */
@WebMvcTest(PlanningController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PlanningController @WebMvcTest")
class PlanningControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private PlanningService planningService;
    @MockitoBean private AutoAffectationService autoAffectationService;
    /**
     * SecurityConfig still wires JwtAuthFilter as a constructor dependency. Even
     * with {@code addFilters = false} disabling the actual filter chain, the bean
     * itself must satisfy injection — we replace it with a Mockito proxy.
     */
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    private static final String CRENEAU_ID = "crn-111";

    private CreneauAssigneDto validDto() {
        // Sprint 16 : CreneauAssigneDto has +1 field `role` at the end (nullable).
        return new CreneauAssigneDto(CRENEAU_ID, "emp-1", 2, 9.0, 17.0, "2026-W15", "site-1", null, null);
    }

    private CreneauAssigne sampleEntity() {
        return CreneauAssigne.builder()
                .id(CRENEAU_ID)
                .employeId("emp-1")
                .jour(2)
                .heureDebut(9.0)
                .heureFin(17.0)
                .semaine("2026-W15")
                .siteId("site-1")
                .organisationId("org-1")
                .build();
    }

    // ── PUT /api/v1/creneaux/{id} : drag-drop endpoint ────────────────────

    @Test
    @DisplayName("PUT /{id} returns 200 with updated creneau on valid payload")
    void update_returns200OnHappyPath() throws Exception {
        when(planningService.update(eq(CRENEAU_ID), any())).thenReturn(sampleEntity());

        mockMvc.perform(put("/api/v1/creneaux/{id}", CRENEAU_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CRENEAU_ID))
                .andExpect(jsonPath("$.employeId").value("emp-1"))
                .andExpect(jsonPath("$.jour").value(2))
                .andExpect(jsonPath("$.heureDebut").value(9.0))
                .andExpect(jsonPath("$.heureFin").value(17.0));
    }

    @Test
    @DisplayName("PUT /{id} returns 422 when service throws BusinessRuleException (BE-04 overlap)")
    void update_returns422OnBusinessRuleViolation() throws Exception {
        when(planningService.update(eq(CRENEAU_ID), any()))
                .thenThrow(new BusinessRuleException("Conflit horaire : un autre creneau existe deja"));

        mockMvc.perform(put("/api/v1/creneaux/{id}", CRENEAU_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Conflit horaire : un autre creneau existe deja"));
    }

    @Test
    @DisplayName("PUT /{id} returns 422 when service throws BusinessRuleException (BE-04 conge approuve)")
    void update_returns422OnApprovedLeaveConflict() throws Exception {
        when(planningService.update(eq(CRENEAU_ID), any()))
                .thenThrow(new BusinessRuleException("Conflit conge : l'employe a un conge approuve sur le 2026-04-08"));

        mockMvc.perform(put("/api/v1/creneaux/{id}", CRENEAU_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Conflit conge")));
    }

    @Test
    @DisplayName("PUT /{id} returns 404 when service throws ResourceNotFoundException")
    void update_returns404WhenCreneauNotFound() throws Exception {
        when(planningService.update(eq(CRENEAU_ID), any()))
                .thenThrow(new ResourceNotFoundException("Creneau", CRENEAU_ID));

        mockMvc.perform(put("/api/v1/creneaux/{id}", CRENEAU_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Ressource introuvable."));
    }

    @Test
    @DisplayName("PUT /{id} returns 400 on DTO validation failure (heureFin out of range, no service call)")
    void update_returns400OnInvalidDto() throws Exception {
        // heureFin=25 violates @Max(24) on CreneauAssigneDto.heureFin
        CreneauAssigneDto invalid = new CreneauAssigneDto(
                CRENEAU_ID, "emp-1", 2, 9.0, 25.0, "2026-W15", "site-1", null, null);

        mockMvc.perform(put("/api/v1/creneaux/{id}", CRENEAU_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation échouée"))
                .andExpect(jsonPath("$.details.heureFin").exists());

        verify(planningService, never()).update(any(), any());
    }

    @Test
    @DisplayName("PUT /{id} returns 400 when employeId is blank (no service call)")
    void update_returns400OnBlankEmployeId() throws Exception {
        CreneauAssigneDto invalid = new CreneauAssigneDto(
                CRENEAU_ID, "", 2, 9.0, 17.0, "2026-W15", "site-1", null, null);

        mockMvc.perform(put("/api/v1/creneaux/{id}", CRENEAU_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.employeId").exists());

        verify(planningService, never()).update(any(), any());
    }

    // ── POST / DELETE / GET smoke coverage ────────────────────────────────

    @Test
    @DisplayName("POST / returns 201 with created creneau")
    void create_returns201() throws Exception {
        when(planningService.create(any())).thenReturn(sampleEntity());

        mockMvc.perform(post("/api/v1/creneaux")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CRENEAU_ID))
                .andExpect(jsonPath("$.employeId").value("emp-1"));
    }

    @Test
    @DisplayName("POST / returns 400 on invalid payload")
    void create_returns400OnInvalidDto() throws Exception {
        CreneauAssigneDto invalid = new CreneauAssigneDto(
                null, "emp-1", 9, 9.0, 17.0, "2026-W15", "site-1", null, null); // jour=9 > @Max(6)

        mockMvc.perform(post("/api/v1/creneaux")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.jour").exists());

        verify(planningService, never()).create(any());
    }

    @Test
    @DisplayName("DELETE /{id} returns 204 No Content")
    void delete_returns204() throws Exception {
        doNothing().when(planningService).delete(CRENEAU_ID);

        mockMvc.perform(delete("/api/v1/creneaux/{id}", CRENEAU_ID))
                .andExpect(status().isNoContent());

        verify(planningService).delete(CRENEAU_ID);
    }

    @Test
    @DisplayName("DELETE /{id} returns 404 when creneau not found")
    void delete_returns404WhenNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Creneau", CRENEAU_ID))
                .when(planningService).delete(CRENEAU_ID);

        mockMvc.perform(delete("/api/v1/creneaux/{id}", CRENEAU_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} returns 200 with creneau")
    void findById_returns200() throws Exception {
        when(planningService.findById(CRENEAU_ID)).thenReturn(sampleEntity());

        mockMvc.perform(get("/api/v1/creneaux/{id}", CRENEAU_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CRENEAU_ID))
                .andExpect(jsonPath("$.semaine").value("2026-W15"));
    }

    @Test
    @DisplayName("GET /{id} returns 404 when creneau not found")
    void findById_returns404() throws Exception {
        when(planningService.findById(CRENEAU_ID))
                .thenThrow(new ResourceNotFoundException("Creneau", CRENEAU_ID));

        mockMvc.perform(get("/api/v1/creneaux/{id}", CRENEAU_ID))
                .andExpect(status().isNotFound());
    }

    /**
     * V33-bis BE : Previously this case bubbled up as a 500 because the
     * {@code @Pattern} constraint on a {@code @PathVariable} raises a
     * {@link jakarta.validation.ConstraintViolationException} (or
     * {@link org.springframework.web.method.annotation.HandlerMethodValidationException})
     * which was not mapped by {@code GlobalExceptionHandler}. The two new handlers
     * added in Sprint 10 now return 400 with a {@code details} map. This regression
     * test locks that contract in place.
     */
    @Test
    @DisplayName("GET /semaine/{semaine} returns 400 when semaine pattern is invalid (V33-bis BE)")
    void findBySemaine_returns400OnInvalidPattern() throws Exception {
        mockMvc.perform(get("/api/v1/creneaux/semaine/{semaine}", "not-a-week"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation échouée"))
                .andExpect(jsonPath("$.details").exists());

        verify(planningService, never()).findBySemaine(org.mockito.ArgumentMatchers.anyString());
    }
}

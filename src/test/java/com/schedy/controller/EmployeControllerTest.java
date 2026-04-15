package com.schedy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schedy.config.JwtAuthFilter;
import com.schedy.dto.EmployeDto;
import com.schedy.dto.request.FindByPinRequest;
import com.schedy.dto.request.UpdateSystemRoleRequest;
import com.schedy.dto.response.EmployeImpactResponse;
import com.schedy.dto.response.EmployeResponse;
import com.schedy.entity.Employe;
import com.schedy.entity.User;
import com.schedy.exception.BusinessRuleException;
import com.schedy.exception.ResourceNotFoundException;
import com.schedy.service.EmployeService;
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
import java.util.Map;
import java.util.Optional;

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
 * BE-05 / Sprint 10 : {@code @WebMvcTest} slice for {@link EmployeController}.
 *
 * <p>Covers the employee CRUD + the two high-stakes side flows : the PIN lookup
 * (used by kiosk pointage) and the impact pre-check (used by the admin delete
 * confirmation dialog, so the user sees the blast radius before accepting a
 * hard delete).</p>
 */
@WebMvcTest(EmployeController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EmployeController @WebMvcTest")
class EmployeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EmployeService employeService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;

    private static final String EMPLOYE_ID = "emp-1";

    // ── Fixtures ─────────────────────────────────────────────────────────

    private Employe sampleEmploye() {
        return Employe.builder()
                .id(EMPLOYE_ID)
                .nom("Alice Martin")
                .roles(List.of("Cuisinier"))
                .telephone("514-555-0100")
                .email("alice@example.com")
                .dateNaissance(LocalDate.of(1990, 5, 12))
                .dateEmbauche(LocalDate.of(2023, 1, 15))
                .organisationId("org-1")
                .build();
    }

    private EmployeResponse sampleResponse() {
        return EmployeResponse.from(sampleEmploye(), null);
    }

    private EmployeDto validDto() {
        return new EmployeDto(
                EMPLOYE_ID, "Alice Martin", List.of("Cuisinier"), "514-555-0100",
                "alice@example.com",
                LocalDate.of(1990, 5, 12),
                LocalDate.of(2023, 1, 15),
                "1234", "org-1",
                List.of(),
                List.of("site-1"),
                null, null); // V38 : numeroEmploye + genre (optional)
    }

    // ── GET / ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET / returns 200 with paged employees")
    void findAll_returns200() throws Exception {
        when(employeService.findAllUserMapByOrg()).thenReturn(Map.<String, User>of());
        Page<Employe> page = new PageImpl<>(List.of(sampleEmploye()));
        when(employeService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/employes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(EMPLOYE_ID))
                .andExpect(jsonPath("$.content[0].nom").value("Alice Martin"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("Cuisinier"));
    }

    @Test
    @DisplayName("GET / with siteId param filters by site")
    void findAll_filtersBySite() throws Exception {
        when(employeService.findAllUserMapByOrg()).thenReturn(Map.<String, User>of());
        Page<Employe> page = new PageImpl<>(List.of(sampleEmploye()));
        when(employeService.findBySiteId(eq("site-1"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/employes").param("siteId", "site-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(EMPLOYE_ID));

        verify(employeService).findBySiteId(eq("site-1"), any(Pageable.class));
    }

    // ── GET /{id} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id} returns 200 with employee")
    void findById_returns200() throws Exception {
        when(employeService.findById(EMPLOYE_ID)).thenReturn(sampleEmploye());
        when(employeService.toResponseWithUser(any(Employe.class))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/employes/{id}", EMPLOYE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EMPLOYE_ID))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("GET /{id} returns 404 when not found")
    void findById_returns404() throws Exception {
        when(employeService.findById(EMPLOYE_ID))
                .thenThrow(new ResourceNotFoundException("Employe", EMPLOYE_ID));

        mockMvc.perform(get("/api/v1/employes/{id}", EMPLOYE_ID))
                .andExpect(status().isNotFound());
    }

    // ── POST / ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST / returns 201 with created employee")
    void create_returns201() throws Exception {
        when(employeService.create(any(EmployeDto.class))).thenReturn(sampleEmploye());
        when(employeService.toResponseWithUser(any(Employe.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/employes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EMPLOYE_ID))
                .andExpect(jsonPath("$.nom").value("Alice Martin"));
    }

    @Test
    @DisplayName("POST / returns 400 when nom is blank (no service call)")
    void create_returns400OnBlankNom() throws Exception {
        EmployeDto invalid = new EmployeDto(
                null, "", List.of("Cuisinier"), null, "alice@example.com",
                null, null, "1234", "org-1", List.of(), List.of(),
                null, null); // V38

        mockMvc.perform(post("/api/v1/employes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.nom").exists());

        verify(employeService, never()).create(any());
    }

    @Test
    @DisplayName("POST / returns 400 when pin is shorter than 4 characters")
    void create_returns400OnShortPin() throws Exception {
        EmployeDto invalid = new EmployeDto(
                null, "Alice", List.of("Cuisinier"), null, "alice@example.com",
                null, null, "12", "org-1", List.of(), List.of(),
                null, null); // V38

        mockMvc.perform(post("/api/v1/employes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.pin").exists());

        verify(employeService, never()).create(any());
    }

    // ── PUT /{id} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /{id} returns 200 with updated employee")
    void update_returns200() throws Exception {
        when(employeService.update(eq(EMPLOYE_ID), any(EmployeDto.class))).thenReturn(sampleEmploye());
        when(employeService.toResponseWithUser(any(Employe.class))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/employes/{id}", EMPLOYE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EMPLOYE_ID));
    }

    @Test
    @DisplayName("PUT /{id} returns 404 when not found")
    void update_returns404() throws Exception {
        when(employeService.update(eq(EMPLOYE_ID), any(EmployeDto.class)))
                .thenThrow(new ResourceNotFoundException("Employe", EMPLOYE_ID));

        mockMvc.perform(put("/api/v1/employes/{id}", EMPLOYE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /{id} returns 422 when service raises BusinessRuleException (pin duplicate)")
    void update_returns422OnPinDuplicate() throws Exception {
        when(employeService.update(eq(EMPLOYE_ID), any(EmployeDto.class)))
                .thenThrow(new BusinessRuleException("Ce PIN est deja utilise par un autre employe"));

        mockMvc.perform(put("/api/v1/employes/{id}", EMPLOYE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validDto())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Ce PIN est deja utilise par un autre employe"));
    }

    // ── DELETE /{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /{id} returns 204")
    void delete_returns204() throws Exception {
        doNothing().when(employeService).delete(EMPLOYE_ID);

        mockMvc.perform(delete("/api/v1/employes/{id}", EMPLOYE_ID))
                .andExpect(status().isNoContent());

        verify(employeService).delete(EMPLOYE_ID);
    }

    @Test
    @DisplayName("DELETE /{id} returns 404 when not found")
    void delete_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Employe", EMPLOYE_ID))
                .when(employeService).delete(EMPLOYE_ID);

        mockMvc.perform(delete("/api/v1/employes/{id}", EMPLOYE_ID))
                .andExpect(status().isNotFound());
    }

    // ── POST /find-by-pin ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /find-by-pin returns 200 when PIN matches")
    void findByPin_returns200() throws Exception {
        when(employeService.findByPin("1234")).thenReturn(Optional.of(sampleEmploye()));

        mockMvc.perform(post("/api/v1/employes/find-by-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FindByPinRequest("1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EMPLOYE_ID))
                .andExpect(jsonPath("$.nom").value("Alice Martin"));
    }

    @Test
    @DisplayName("POST /find-by-pin returns 404 when no employee matches")
    void findByPin_returns404() throws Exception {
        when(employeService.findByPin("9999")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/employes/find-by-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FindByPinRequest("9999"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /find-by-pin returns 400 when pin is shorter than 4 chars")
    void findByPin_returns400OnShortPin() throws Exception {
        mockMvc.perform(post("/api/v1/employes/find-by-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FindByPinRequest("12"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.pin").exists());

        verify(employeService, never()).findByPin(anyString());
    }

    // ── GET /{id}/impact ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id}/impact returns 200 with blast-radius summary")
    void getImpact_returns200() throws Exception {
        EmployeImpactResponse impact = new EmployeImpactResponse(
                12, 3, 2, 1, 0, 45, true);
        when(employeService.getImpact(EMPLOYE_ID)).thenReturn(impact);

        mockMvc.perform(get("/api/v1/employes/{id}/impact", EMPLOYE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creneauxFuturs").value(12))
                .andExpect(jsonPath("$.creneauxSemaineCourante").value(3))
                .andExpect(jsonPath("$.nbSites").value(2))
                .andExpect(jsonPath("$.nbPointages").value(45))
                .andExpect(jsonPath("$.hasCompteManager").value(true));
    }

    // ── PUT /{id}/system-role ────────────────────────────────────────────

    @Test
    @DisplayName("PUT /{id}/system-role returns 200 with email-sent result on promotion")
    void updateSystemRole_returns200OnPromotion() throws Exception {
        when(employeService.updateSystemRole(eq(EMPLOYE_ID), any(UpdateSystemRoleRequest.class)))
                .thenReturn(Map.of("emailSent", true, "email", "alice@example.com"));

        mockMvc.perform(put("/api/v1/employes/{id}/system-role", EMPLOYE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSystemRoleRequest("MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailSent").value(true))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("PUT /{id}/system-role returns 204 when demotion (no email sent)")
    void updateSystemRole_returns204OnDemotion() throws Exception {
        when(employeService.updateSystemRole(eq(EMPLOYE_ID), any(UpdateSystemRoleRequest.class)))
                .thenReturn(null);

        mockMvc.perform(put("/api/v1/employes/{id}/system-role", EMPLOYE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSystemRoleRequest("EMPLOYEE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PUT /{id}/system-role returns 400 when systemRole is blank")
    void updateSystemRole_returns400OnBlank() throws Exception {
        mockMvc.perform(put("/api/v1/employes/{id}/system-role", EMPLOYE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSystemRoleRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.systemRole").exists());

        verify(employeService, never()).updateSystemRole(anyString(), any());
    }
}

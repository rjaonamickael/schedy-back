package com.schedy.controller;

import com.schedy.dto.EmployeDto;
import com.schedy.dto.request.FindByPinRequest;
import com.schedy.dto.request.UpdateSystemRoleRequest;
import com.schedy.dto.response.EmployeImpactResponse;
import com.schedy.dto.response.EmployeResponse;
import com.schedy.entity.User;
import com.schedy.service.EmployeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employes")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class EmployeController {

    private final EmployeService employeService;

    @GetMapping
    public ResponseEntity<Page<EmployeResponse>> findAll(Pageable pageable,
            @RequestParam(value = "siteId", required = false) String siteId) {
        Map<String, User> userMap = employeService.findAllUserMapByOrg();
        if (siteId != null) {
            return ResponseEntity.ok(employeService.findBySiteId(siteId, pageable)
                    .map(e -> EmployeResponse.from(e, userMap.get(e.getId()))));
        }
        return ResponseEntity.ok(employeService.findAll(pageable)
                .map(e -> EmployeResponse.from(e, userMap.get(e.getId()))));
    }

    @GetMapping("/all")
    public ResponseEntity<List<EmployeResponse>> findAll(
            @RequestParam(value = "siteId", required = false) String siteId) {
        Map<String, User> userMap = employeService.findAllUserMapByOrg();
        Page<EmployeResponse> page;
        if (siteId != null) {
            page = employeService.findBySiteId(siteId, PageRequest.of(0, 5000))
                    .map(e -> EmployeResponse.from(e, userMap.get(e.getId())));
        } else {
            page = employeService.findAll(PageRequest.of(0, 5000))
                    .map(e -> EmployeResponse.from(e, userMap.get(e.getId())));
        }
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.getTotalElements()))
                .body(page.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(employeService.toResponseWithUser(employeService.findById(id)));
    }

    /**
     * Returns the decrypted PIN for an employee via POST (avoids PIN in URL/logs).
     * The PIN is stored AES-256-GCM encrypted in the database and decrypted here
     * only for authorised callers.
     *
     * Access rules:
     * <ul>
     *   <li>ADMIN or MANAGER: may retrieve the PIN for any employee in their org.</li>
     *   <li>EMPLOYEE: may only retrieve their own PIN.</li>
     * </ul>
     */
    @PostMapping("/{id}/pin")
    public ResponseEntity<java.util.Map<String, String>> getPin(
            @PathVariable String id,
            org.springframework.security.core.Authentication authentication) {
        boolean isAdminOrManager = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!isAdminOrManager) {
            String callerEmail = authentication.getName();
            var user = employeService.findUserByEmail(callerEmail);
            if (user == null || !id.equals(user.getEmployeId())) {
                return ResponseEntity.status(403).build();
            }
        }
        String pin = employeService.getDecryptedPin(id);
        return ResponseEntity.ok(java.util.Map.of("pin", pin));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<EmployeResponse>> findByRole(@PathVariable String role) {
        return ResponseEntity.ok(employeService.findByRole(role).stream().map(EmployeResponse::from).toList());
    }

    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<EmployeResponse>> findBySiteId(@PathVariable String siteId) {
        return ResponseEntity.ok(employeService.findBySiteId(siteId).stream().map(EmployeResponse::from).toList());
    }

    @PostMapping("/find-by-pin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeResponse> findByPin(@Valid @RequestBody FindByPinRequest request) {
        return employeService.findByPin(request.pin())
                .map(EmployeResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeResponse> create(@Valid @RequestBody EmployeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeService.toResponseWithUser(employeService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EmployeResponse> update(@PathVariable String id, @Valid @RequestBody EmployeDto dto) {
        return ResponseEntity.ok(employeService.toResponseWithUser(employeService.update(id, dto)));
    }

    /**
     * Promotes or demotes an employee's system role.
     * Accepts: systemRole = "MANAGER" | "EMPLOYEE".
     *
     * When a new User account is created (promotion only), an invitation email is sent
     * and the response body contains: { "emailSent": true, "email": "..." }.
     * If the account already existed, or the operation is a demotion, returns 204 No Content.
     */
    @PutMapping("/{id}/system-role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateSystemRole(
            @PathVariable String id,
            @Valid @RequestBody UpdateSystemRoleRequest request) {
        Map<String, Object> result = employeService.updateSystemRole(id, request);
        if (result != null) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resend-invitation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resendInvitation(@PathVariable String id) {
        employeService.resendInvitation(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns a lightweight impact summary for the given employee.
     * Called by the frontend BEFORE showing the deletion confirmation dialog
     * so the user is informed of what will be hard-deleted.
     *
     * Restricted to ADMIN because only admins can delete employees.
     */
    @GetMapping("/{id}/impact")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeImpactResponse> getImpact(@PathVariable String id) {
        return ResponseEntity.ok(employeService.getImpact(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        employeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

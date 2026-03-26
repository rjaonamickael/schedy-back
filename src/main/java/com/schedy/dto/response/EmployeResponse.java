package com.schedy.dto.response;

import com.schedy.entity.DisponibilitePlage;
import com.schedy.entity.Employe;
import com.schedy.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EmployeResponse(
        String id,
        String nom,
        String role,
        String telephone,
        String email,
        LocalDate dateNaissance,
        LocalDate dateEmbauche,
        List<DisponibilitePlage> disponibilites,
        List<String> siteIds,
        String systemRole,
        boolean hasUserAccount,
        boolean invitationPending
) {
    /**
     * Build an EmployeResponse with linked User data.
     * linkedUser may be null if the employee has no user account.
     */
    public static EmployeResponse from(Employe e, User linkedUser) {
        boolean pending = linkedUser != null && !linkedUser.isPasswordSet();
        return new EmployeResponse(
                e.getId(),
                e.getNom(),
                e.getRole(),
                e.getTelephone(),
                e.getEmail(),
                e.getDateNaissance(),
                e.getDateEmbauche(),
                e.getDisponibilites(),
                e.getSiteIds(),
                linkedUser != null ? linkedUser.getRole().name() : null,
                linkedUser != null,
                pending
        );
    }

    /**
     * Convenience overload for single-employee lookups where no User map is available.
     * Callers that need systemRole/hasUserAccount should use from(Employe, User) instead.
     */
    public static EmployeResponse from(Employe e) {
        return from(e, null);
    }
}

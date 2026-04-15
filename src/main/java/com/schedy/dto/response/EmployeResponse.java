package com.schedy.dto.response;

import com.schedy.entity.DisponibilitePlage;
import com.schedy.entity.Employe;
import com.schedy.entity.Genre;
import com.schedy.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EmployeResponse(
        String id,
        String nom,
        // Sprint 16 / Feature 2 : hierarchical multi-role.
        // roles[0] = role principal, roles[1..] = roles secondaires.
        List<String> roles,
        String telephone,
        String email,
        LocalDate dateNaissance,
        LocalDate dateEmbauche,
        // V38 : HR number + gender (both nullable).
        String numeroEmploye,
        Genre genre,
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
                e.getRoles(),
                e.getTelephone(),
                e.getEmail(),
                e.getDateNaissance(),
                e.getDateEmbauche(),
                e.getNumeroEmploye(),
                e.getGenre(),
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

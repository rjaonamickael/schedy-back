package com.schedy.dto.response;

import com.schedy.entity.DisponibilitePlage;
import com.schedy.entity.Employe;

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
        List<String> siteIds
) {
    public static EmployeResponse from(Employe e) {
        return new EmployeResponse(
                e.getId(),
                e.getNom(),
                e.getRole(),
                e.getTelephone(),
                e.getEmail(),
                e.getDateNaissance(),
                e.getDateEmbauche(),
                e.getDisponibilites(),
                e.getSiteIds()
        );
    }
}

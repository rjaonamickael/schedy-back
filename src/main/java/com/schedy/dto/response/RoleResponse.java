package com.schedy.dto.response;

import com.schedy.entity.Role;

public record RoleResponse(
        String id,
        String nom,
        int importance,
        String couleur,
        String icone
) {
    public static RoleResponse from(Role r) {
        return new RoleResponse(
                r.getId(),
                r.getNom(),
                r.getImportance(),
                r.getCouleur(),
                r.getIcone()
        );
    }
}

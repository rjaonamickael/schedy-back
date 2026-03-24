package com.schedy.dto.response;

import java.util.List;

public record AutoAffectationResponse(
    int totalAffectes,
    List<CreneauAssigneResponse> creneaux
) {}

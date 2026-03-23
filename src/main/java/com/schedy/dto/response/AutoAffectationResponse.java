package com.schedy.dto.response;

import com.schedy.entity.CreneauAssigne;

import java.util.List;

public record AutoAffectationResponse(
    int totalAffectes,
    List<CreneauAssigne> creneaux
) {}

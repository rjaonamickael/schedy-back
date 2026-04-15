package com.schedy.dto.response;

import com.schedy.entity.FrequenceAccrual;
import com.schedy.entity.Genre;
import com.schedy.entity.TypeConge;
import com.schedy.entity.TypeLimite;
import com.schedy.entity.UniteConge;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public record TypeCongeResponse(
        String id,
        String nom,
        boolean paye,
        UniteConge unite,
        String couleur,
        TypeLimite typeLimite,
        Double quotaAnnuel,
        Double accrualMontant,
        FrequenceAccrual accrualFrequence,
        boolean autoriserDepassement,
        LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        Set<Genre> genresEligibles
) {
    public static TypeCongeResponse from(TypeConge t) {
        return new TypeCongeResponse(
                t.getId(),
                t.getNom(),
                t.isPaye(),
                t.getUnite(),
                t.getCouleur(),
                t.getTypeLimite(),
                t.getQuotaAnnuel(),
                t.getAccrualMontant(),
                t.getAccrualFrequence(),
                t.isAutoriserDepassement(),
                t.getDateDebutValidite(),
                t.getDateFinValidite(),
                t.getGenresEligibles() != null ? new HashSet<>(t.getGenresEligibles()) : new HashSet<>()
        );
    }
}

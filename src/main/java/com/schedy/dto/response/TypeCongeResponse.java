package com.schedy.dto.response;

import com.schedy.entity.CategorieConge;
import com.schedy.entity.FrequenceAccrual;
import com.schedy.entity.TypeConge;
import com.schedy.entity.UniteConge;

public record TypeCongeResponse(
        String id,
        String nom,
        CategorieConge categorie,
        UniteConge unite,
        String couleur,
        String modeQuota,
        boolean quotaIllimite,
        boolean autoriserNegatif,
        Double accrualMontant,
        FrequenceAccrual accrualFrequence,
        Double reportMax,
        Integer reportDuree
) {
    public static TypeCongeResponse from(TypeConge t) {
        return new TypeCongeResponse(
                t.getId(),
                t.getNom(),
                t.getCategorie(),
                t.getUnite(),
                t.getCouleur(),
                t.getModeQuota(),
                t.isQuotaIllimite(),
                t.isAutoriserNegatif(),
                t.getAccrualMontant(),
                t.getAccrualFrequence(),
                t.getReportMax(),
                t.getReportDuree()
        );
    }
}

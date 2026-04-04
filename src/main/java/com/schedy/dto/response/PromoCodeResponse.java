package com.schedy.dto.response;

import com.schedy.entity.PromoCode;

import java.time.OffsetDateTime;

public record PromoCodeResponse(
        String id,
        String code,
        String description,
        Integer discountPercent,
        Integer discountMonths,
        String planOverride,
        Integer maxUses,
        int currentUses,
        OffsetDateTime validFrom,
        OffsetDateTime validTo,
        boolean active
) {
    public static PromoCodeResponse from(PromoCode entity) {
        return new PromoCodeResponse(
                entity.getId(),
                entity.getCode(),
                entity.getDescription(),
                entity.getDiscountPercent(),
                entity.getDiscountMonths(),
                entity.getPlanOverride(),
                entity.getMaxUses(),
                entity.getCurrentUses(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.isActive()
        );
    }
}

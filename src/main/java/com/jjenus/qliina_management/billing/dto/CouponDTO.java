package com.jjenus.qliina_management.billing.dto;

import com.jjenus.qliina_management.billing.model.CouponStatus;
import com.jjenus.qliina_management.billing.model.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CouponDTO(
        UUID id,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        Integer maxRedemptions,
        int redemptionsCount,
        Integer maxRedemptionsPerBusiness,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        CouponStatus status
) {
}

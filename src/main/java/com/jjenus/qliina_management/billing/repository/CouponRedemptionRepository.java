package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    long countByCouponIdAndBusinessId(UUID couponId, UUID businessId);
}

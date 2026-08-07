package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.SubscriptionCoupon;
import com.jjenus.qliina_management.billing.model.SubscriptionCouponId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SubscriptionCouponRepository extends JpaRepository<SubscriptionCoupon, SubscriptionCouponId> {

    boolean existsByIdSubscriptionId(UUID subscriptionId);
}

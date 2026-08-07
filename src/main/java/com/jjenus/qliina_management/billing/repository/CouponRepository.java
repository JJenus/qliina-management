package com.jjenus.qliina_management.billing.repository;

import com.jjenus.qliina_management.billing.model.Coupon;
import com.jjenus.qliina_management.billing.model.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCode(String code);

    Optional<Coupon> findByCodeAndStatus(String code, CouponStatus status);

    boolean existsByCode(String code);

    /**
     * Atomic conditional redemption — the race guard from MD §4. Returns the
     * number of affected rows: 0 means the coupon is exhausted.
     */
    @Modifying
    @Query(value = "UPDATE billing_coupons SET redemptions_count = redemptions_count + 1, " +
            "updated_at = :now WHERE id = :id " +
            "AND (max_redemptions IS NULL OR redemptions_count < max_redemptions)", nativeQuery = true)
    int incrementRedemptionsIfAvailable(@Param("id") UUID id, @Param("now") LocalDateTime now);
}

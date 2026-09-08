package com.jjenus.qliina_management.common.seed;

import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.model.PlanFeature;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.model.PlanVersion;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.billing.repository.PlanFeatureRepository;
import com.jjenus.qliina_management.billing.repository.PlanVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds the base billing plans (FREE / STARTER / PRO). Reference data — runs in
 * all profiles, create-only and idempotent. ENTERPRISE is not seeded; it is
 * created manually per customer via the admin billing API.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class BillingPlanSeeder implements CommandLineRunner {

    private final BillingPlanRepository   billingPlanRepository;
    private final PlanVersionRepository   planVersionRepository;
    private final PlanFeatureRepository   planFeatureRepository;

    private static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final Set<String> HARD_LIMIT_KEYS = Set.of(
            "max_shops", "max_users", "max_employees", "max_orders_per_month",
            "max_service_catalog_items", "max_inventory_items", "data_retention_days");

    @Override
    @Transactional
    public void run(String... args) {
        seedBillingPlans();
    }

    private void seedBillingPlans() {
        log.info("Seeding billing plans...");
        seedBillingPlan("FREE", "Free",
                "Perfect for getting started — no credit card required.",
                BigDecimal.ZERO, features(
                        "max_shops", "1", "max_users", "3", "max_employees", "10",
                        "max_orders_per_month", "500", "max_service_catalog_items", "20",
                        "max_inventory_items", "50", "data_retention_days", "365",
                        "advancedAnalytics", "false", "exportReports", "false",
                        "loyaltyProgram", "true", "qualityControl", "true",
                        "apiAccess", "false", "prioritySupport", "false",
                        "customBranding", "false", "multiShopReporting", "false"));

        seedBillingPlan("STARTER", "Starter",
                "Grow across multiple locations with core business tools.",
                new BigDecimal("9900.00"), features(
                        "max_shops", "3", "max_users", "15", "max_employees", "30",
                        "max_orders_per_month", "2000", "max_service_catalog_items", "100",
                        "max_inventory_items", "300", "data_retention_days", "730",
                        "advancedAnalytics", "false", "exportReports", "false",
                        "loyaltyProgram", "true", "qualityControl", "true",
                        "apiAccess", "false", "prioritySupport", "false",
                        "customBranding", "false", "multiShopReporting", "true"));

        seedBillingPlan("PRO", "Pro",
                "Unlimited growth with advanced analytics, exports and API access.",
                new BigDecimal("24900.00"), features(
                        "max_shops", "-1", "max_users", "-1", "max_employees", "-1",
                        "max_orders_per_month", "-1", "max_service_catalog_items", "-1",
                        "max_inventory_items", "-1", "data_retention_days", "1825",
                        "advancedAnalytics", "true", "exportReports", "true",
                        "loyaltyProgram", "true", "qualityControl", "true",
                        "apiAccess", "true", "prioritySupport", "true",
                        "customBranding", "true", "multiShopReporting", "true"));

        log.info("Billing plans seeded. Total: {}", billingPlanRepository.count());
    }

    private void seedBillingPlan(String name, String displayName, String description, BigDecimal price,
                                 Map<String, String> features) {
        boolean exists = billingPlanRepository.findAll().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
        if (exists) {
            log.debug("Billing plan '{}' already exists — skipping.", name);
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        BillingPlan plan = BillingPlan.builder()
                .name(displayName).description(description).status(PlanStatus.ACTIVE).build();
        plan.setCreatedAt(now);
        plan.setCreatedBy(SYSTEM_USER_ID);
        plan = billingPlanRepository.save(plan);

        PlanVersion version = PlanVersion.builder()
                .plan(plan).price(price).currency("NGN").effectiveFrom(now).build();
        version.setCreatedAt(now);
        version.setCreatedBy(SYSTEM_USER_ID);
        planVersionRepository.save(version);

        for (Map.Entry<String, String> e : features.entrySet()) {
            PlanFeature feature = PlanFeature.builder()
                    .plan(plan).featureKey(e.getKey()).value(e.getValue())
                    .isHardLimit(HARD_LIMIT_KEYS.contains(e.getKey())).build();
            feature.setCreatedAt(now);
            feature.setCreatedBy(SYSTEM_USER_ID);
            planFeatureRepository.save(feature);
        }
        log.info("Seeded billing plan: {} ({} {}/month)", displayName, price, "NGN");
    }

    private Map<String, String> features(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
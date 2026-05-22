package com.jjenus.qliina_management.business.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DB-persisted subscription plan that defines feature flags and hard limits
 * for each tier. One row per tier (FREE / STARTER / PRO / ENTERPRISE).
 *
 * Note: DDL requires the table to be created before switching ddl-auto back
 * to "validate". Run with ddl-auto=update once, then revert.
 *
 *   CREATE TABLE subscription_plans (
 *     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *     tier VARCHAR(20) NOT NULL UNIQUE,
 *     name VARCHAR(100) NOT NULL,
 *     description TEXT,
 *     price DECIMAL(10,2) NOT NULL DEFAULT 0,
 *     max_shops INT NOT NULL DEFAULT 1,
 *     max_users INT NOT NULL DEFAULT 3,
 *     max_employees INT NOT NULL DEFAULT 10,
 *     max_orders_per_month INT NOT NULL DEFAULT 500,
 *     max_service_catalog_items INT NOT NULL DEFAULT 20,
 *     max_inventory_items INT NOT NULL DEFAULT 50,
 *     data_retention_days INT NOT NULL DEFAULT 365,
 *     advanced_analytics BOOLEAN NOT NULL DEFAULT FALSE,
 *     export_reports BOOLEAN NOT NULL DEFAULT FALSE,
 *     loyalty_program BOOLEAN NOT NULL DEFAULT TRUE,
 *     quality_control BOOLEAN NOT NULL DEFAULT TRUE,
 *     api_access BOOLEAN NOT NULL DEFAULT FALSE,
 *     priority_support BOOLEAN NOT NULL DEFAULT FALSE,
 *     custom_branding BOOLEAN NOT NULL DEFAULT FALSE,
 *     multi_shop_reporting BOOLEAN NOT NULL DEFAULT FALSE,
 *     is_active BOOLEAN NOT NULL DEFAULT TRUE,
 *     created_at TIMESTAMP DEFAULT NOW(),
 *     updated_at TIMESTAMP DEFAULT NOW()
 *   );
 */
@Entity
@Table(name = "subscription_plans")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Matches Business.Plan enum name — FREE, STARTER, PRO, ENTERPRISE */
    @Column(length = 20, unique = true, nullable = false)
    private String tier;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Monthly price in base currency (NGN by default). */
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    // ---- Hard limits. -1 = unlimited. ----

    @Column(nullable = false)
    private int maxShops = 1;

    @Column(nullable = false)
    private int maxUsers = 3;

    @Column(nullable = false)
    private int maxEmployees = 10;

    @Column(nullable = false)
    private int maxOrdersPerMonth = 500;

    @Column(nullable = false)
    private int maxServiceCatalogItems = 20;

    @Column(nullable = false)
    private int maxInventoryItems = 50;

    @Column(nullable = false)
    private int dataRetentionDays = 365;

    // ---- Feature flags ----

    @Column(nullable = false)
    private boolean advancedAnalytics = false;

    @Column(nullable = false)
    private boolean exportReports = false;

    @Column(nullable = false)
    private boolean loyaltyProgram = true;

    @Column(nullable = false)
    private boolean qualityControl = true;

    @Column(nullable = false)
    private boolean apiAccess = false;

    @Column(nullable = false)
    private boolean prioritySupport = false;

    @Column(nullable = false)
    private boolean customBranding = false;

    @Column(nullable = false)
    private boolean multiShopReporting = false;

    @Column(nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

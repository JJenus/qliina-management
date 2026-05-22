package com.jjenus.qliina_management.business.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Full plan definition, editable by SUPER_ADMIN / PLATFORM_ADMIN. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubscriptionPlanDTO {

    private UUID       id;
    private String     tier;
    private String     name;
    private String     description;
    private BigDecimal price;

    // Limits
    private int maxShops;
    private int maxUsers;
    private int maxEmployees;
    private int maxOrdersPerMonth;
    private int maxServiceCatalogItems;
    private int maxInventoryItems;
    private int dataRetentionDays;

    // Feature flags
    private boolean advancedAnalytics;
    private boolean exportReports;
    private boolean loyaltyProgram;
    private boolean qualityControl;
    private boolean apiAccess;
    private boolean prioritySupport;
    private boolean customBranding;
    private boolean multiShopReporting;
    private boolean isActive;
}

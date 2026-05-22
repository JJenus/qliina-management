package com.jjenus.qliina_management.business.dto;

import lombok.*;

import java.time.LocalDateTime;

/** Current plan + live usage for a business, returned to the frontend. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PlanUsageDTO {

    // Plan identity
    private String planName;
    private String tier;
    private String status;
    private LocalDateTime trialEndsAt;
    private boolean isTrialExpired;
    private int daysLeftInTrial;  // -1 if no trial

    // Usage counters
    private long   activeShops;
    private int    maxShops;
    private double shopsUsageRatio;

    private long   activeUsers;
    private int    maxUsers;
    private double usersUsageRatio;

    private long   activeEmployees;
    private int    maxEmployees;
    private double employeesUsageRatio;

    private long   ordersThisMonth;
    private int    maxOrdersPerMonth;
    private double ordersUsageRatio;

    // Feature flags pass-through
    private boolean advancedAnalytics;
    private boolean exportReports;
    private boolean loyaltyProgram;
    private boolean qualityControl;
    private boolean apiAccess;
    private boolean prioritySupport;
    private boolean customBranding;
    private boolean multiShopReporting;
}

package com.jjenus.qliina_management.reporting.controller;

import com.jjenus.qliina_management.reporting.dto.PlatformStats;
import com.jjenus.qliina_management.reporting.service.PlatformStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide analytics for the admin dashboard. Read-only aggregates over
 * all tenants — no business-scoped data is exposed, only rollups.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private static final String STATS = "hasPermission(null, 'PLATFORM', 'platform.stats.view')";
    private static final String BILLING = "hasPermission(null, 'PLATFORM', 'platform.billing.manage')";

    private final PlatformStatsService statsService;

    @GetMapping("/overview")
    @PreAuthorize(STATS + " or " + BILLING)
    public ResponseEntity<PlatformStats.Overview> overview() {
        return ResponseEntity.ok(statsService.overview());
    }

    @GetMapping("/revenue")
    @PreAuthorize(STATS + " or " + BILLING)
    public ResponseEntity<PlatformStats.Revenue> revenue() {
        return ResponseEntity.ok(statsService.revenue());
    }

    @GetMapping("/growth")
    @PreAuthorize(STATS + " or " + BILLING)
    public ResponseEntity<PlatformStats.Growth> growth() {
        return ResponseEntity.ok(statsService.growth());
    }

    @GetMapping("/dunning")
    @PreAuthorize(STATS + " or " + BILLING)
    public ResponseEntity<PlatformStats.Dunning> dunning() {
        return ResponseEntity.ok(statsService.dunning());
    }
}

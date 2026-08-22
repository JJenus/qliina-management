// ./src/main/java/com/jjenus/qliina_management/order/controller/OrderDiscrepancyController.java
package com.jjenus.qliina_management.order.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import com.jjenus.qliina_management.order.dto.CreateDiscrepancyRequest;
import com.jjenus.qliina_management.order.dto.OrderDiscrepancyDTO;
import com.jjenus.qliina_management.order.dto.ReviewDiscrepancyRequest;
import com.jjenus.qliina_management.order.service.OrderDiscrepancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Discrepancy reporting flow.
 *
 * Worker endpoints (order.view): report an issue on an order, list own reports.
 * Front desk / manager endpoints (report.view.operational / order.status.update):
 * open-reports queue and resolution workflow.
 */
@Tag(name = "Order Discrepancies", description = "Worker-reported order issues and their resolution")
@RestController
@RequestMapping("/api/v1/{businessId}")
@RequiredArgsConstructor
public class OrderDiscrepancyController {

    private final OrderDiscrepancyService discrepancyService;

    // ── worker ───────────────────────────────────────────────────────────────

    @Operation(summary = "Report an order discrepancy",
        description = "Workers report problems found while processing an order " +
                     "(code mismatch, count off, damaged or missing pieces). " +
                     "Front desk and managerial roles are notified.")
    @PostMapping("/worker/orders/discrepancies")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<OrderDiscrepancyDTO> create(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateDiscrepancyRequest request) {
        UUID reporterId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(discrepancyService.create(businessId, reporterId, request));
    }

    @Operation(summary = "My discrepancy reports")
    @GetMapping("/worker/orders/discrepancies")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PageResponse<OrderDiscrepancyDTO>> myReports(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        UUID reporterId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(discrepancyService.myReports(businessId, reporterId, pageable));
    }

    // ── front desk / manager ─────────────────────────────────────────────────

    @Operation(summary = "Discrepancy queue",
        description = "All reports for the business, optionally filtered by status " +
                     "(OPEN | ACKNOWLEDGED | RESOLVED | DISMISSED).")
    @GetMapping("/orders/discrepancies")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<PageResponse<OrderDiscrepancyDTO>> list(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @Parameter(description = "Status filter")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(discrepancyService.list(businessId, status, pageable));
    }

    @Operation(summary = "Open discrepancy count",
        description = "Badge-friendly count of OPEN reports for the business.")
    @GetMapping("/orders/discrepancies/open-count")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<Map<String, Long>> openCount(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(Map.of("open", discrepancyService.countOpen(businessId)));
    }

    @Operation(summary = "Review a discrepancy",
        description = "action: ACKNOWLEDGE | RESOLVE | DISMISS. The reporter is notified.")
    @PatchMapping("/orders/discrepancies/{discrepancyId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.status.update')")
    public ResponseEntity<OrderDiscrepancyDTO> review(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            @PathVariable UUID discrepancyId,
            @Valid @RequestBody ReviewDiscrepancyRequest request) {
        UUID handlerId = SecurityContextUtil.requireUserId();
        return ResponseEntity.ok(discrepancyService.review(businessId, handlerId, discrepancyId, request));
    }
}

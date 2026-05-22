package com.jjenus.qliina_management.business.controller;

import com.jjenus.qliina_management.business.dto.BusinessDTO;
import com.jjenus.qliina_management.business.dto.UpdateBusinessRequest;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.service.BusinessService;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * REST controller for Business management.
 *
 * The open self-registration endpoint lives in AuthController at
 * POST /api/v1/auth/register-business and delegates to BusinessService.
 */
@Tag(name = "Business Management", description = "Business lifecycle and metadata management")
@RestController
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    @Operation(summary = "List all businesses",
               description = "Paginated list of every business on the platform. Superadmin only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.view')")
    public ResponseEntity<PageResponse<BusinessDTO>> listBusinesses(
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) Business.Status status,
            @Parameter(description = "Search by name (partial, case-insensitive)")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(businessService.listBusinesses(status, search, pageable));
    }

    @Operation(summary = "Get business",
               description = "Retrieve metadata for a specific business. Accessible by the owner and Superadmin.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{businessId}")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.view') or hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<BusinessDTO> getBusiness(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId) {
        return ResponseEntity.ok(businessService.getBusiness(businessId));
    }

    @Operation(summary = "Update business",
               description = "Update mutable business fields. Business owner only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{businessId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<BusinessDTO> updateBusiness(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @Valid @RequestBody UpdateBusinessRequest request) {
        return ResponseEntity.ok(businessService.updateBusiness(businessId, request));
    }

    @Operation(summary = "Change business status", description = "Suspend, cancel, or re-activate. Superadmin only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{businessId}/status")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.manage')")
    public ResponseEntity<BusinessDTO> changeStatus(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @Parameter(description = "New status", required = true) @RequestParam Business.Status status) {
        return ResponseEntity.ok(businessService.updateStatus(businessId, status));
    }
}

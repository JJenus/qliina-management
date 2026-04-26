package com.jjenus.qliina_management.business.controller;

import com.jjenus.qliina_management.business.dto.CreateShopRequest;
import com.jjenus.qliina_management.business.dto.ShopDTO;
import com.jjenus.qliina_management.business.dto.UpdateShopRequest;
import com.jjenus.qliina_management.business.service.ShopService;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * REST controller for Shop management within a Business.
 * All routes are tenant-scoped under /api/v1/{businessId}/shops.
 */
@Tag(name = "Shop Management",
     description = "Create, list, update, and deactivate shops within a business")
@RestController
@RequestMapping("/api/v1/{businessId}/shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @Operation(summary = "List shops",
               description = "Paginated list of all shops. Used to populate the Shop Selector in the header.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings') "
                + "or hasPermission(#businessId, 'BUSINESS', 'user.view')")
    public ResponseEntity<PageResponse<ShopDTO>> listShops(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(shopService.listShops(businessId, pageable));
    }

    @Operation(summary = "Get shop")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{shopId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.view')")
    public ResponseEntity<ShopDTO> getShop(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @Parameter(description = "Shop ID",     required = true) @PathVariable UUID shopId) {
        return ResponseEntity.ok(shopService.getShop(businessId, shopId));
    }

    @Operation(summary = "Create shop", description = "Add a new shop. Requires admin.settings permission.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "Validation error or duplicate code",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<ShopDTO> createShop(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @Valid @RequestBody CreateShopRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shopService.createShop(businessId, request));
    }

    @Operation(summary = "Update shop", description = "Update mutable shop fields. Code is immutable.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{shopId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<ShopDTO> updateShop(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @Parameter(description = "Shop ID",     required = true) @PathVariable UUID shopId,
            @Valid @RequestBody UpdateShopRequest request) {
        return ResponseEntity.ok(shopService.updateShop(businessId, shopId, request));
    }

    @Operation(summary = "Deactivate shop", description = "Soft-deactivate. All data is retained.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{shopId}/deactivate")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> deactivateShop(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @Parameter(description = "Shop ID",     required = true) @PathVariable UUID shopId) {
        shopService.deactivateShop(businessId, shopId);
        return ResponseEntity.ok(SuccessResponse.of("Shop deactivated successfully"));
    }

    @Operation(summary = "Reactivate shop", description = "Re-enable a deactivated shop.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{shopId}/reactivate")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<ShopDTO> reactivateShop(
            @Parameter(description = "Business ID", required = true) @PathVariable UUID businessId,
            @Parameter(description = "Shop ID",     required = true) @PathVariable UUID shopId) {
        return ResponseEntity.ok(shopService.reactivateShop(businessId, shopId));
    }
}

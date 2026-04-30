package com.jjenus.qliina_management.business.controller;

import com.jjenus.qliina_management.business.dto.*;
import com.jjenus.qliina_management.business.service.ServiceCatalogService;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Service Catalog", description = "Manage service types, garment types, and pricing")
@RestController
@RequestMapping("/api/v1/{businessId}/catalog")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final ServiceCatalogService catalogService;

    // ==================== Services ====================

    @GetMapping("/services")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<List<ServiceTypeDTO>> listActiveServices(@PathVariable UUID businessId) {
        return ResponseEntity.ok(catalogService.getActiveServices(businessId));
    }

    @GetMapping("/services/all")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<PageResponse<ServiceTypeDTO>> listAllServices(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(catalogService.getAllServices(businessId, search, pageable));
    }

    @PostMapping("/services")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<ServiceTypeDTO> createService(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateServiceTypeRequest request) {
        return ResponseEntity.ok(catalogService.createService(businessId, request));
    }

    @PutMapping("/services/{serviceId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<ServiceTypeDTO> updateService(
            @PathVariable UUID businessId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceTypeRequest request) {
        return ResponseEntity.ok(catalogService.updateService(serviceId, request));
    }

    @DeleteMapping("/services/{serviceId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> deleteService(
            @PathVariable UUID businessId,
            @PathVariable UUID serviceId) {
        catalogService.deleteService(serviceId);
        return ResponseEntity.ok(SuccessResponse.of("Service deleted"));
    }

    // ==================== Garments ====================

    @GetMapping("/garments")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<List<GarmentTypeDTO>> listActiveGarments(@PathVariable UUID businessId) {
        return ResponseEntity.ok(catalogService.getActiveGarments(businessId));
    }

    @GetMapping("/garments/all")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<PageResponse<GarmentTypeDTO>> listAllGarments(
            @PathVariable UUID businessId,
            @PageableDefault(size = 20, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(catalogService.getAllGarments(businessId, pageable));
    }

    @PostMapping("/garments")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<GarmentTypeDTO> createGarment(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateGarmentTypeRequest request) {
        return ResponseEntity.ok(catalogService.createGarment(businessId, request));
    }

    @PutMapping("/garments/{garmentId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<GarmentTypeDTO> updateGarment(
            @PathVariable UUID businessId,
            @PathVariable UUID garmentId,
            @Valid @RequestBody UpdateGarmentTypeRequest request) {
        return ResponseEntity.ok(catalogService.updateGarment(garmentId, request));
    }

    @DeleteMapping("/garments/{garmentId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> deleteGarment(
            @PathVariable UUID businessId,
            @PathVariable UUID garmentId) {
        catalogService.deleteGarment(garmentId);
        return ResponseEntity.ok(SuccessResponse.of("Garment deleted"));
    }

    // ==================== Pricing ====================

    @GetMapping("/pricing")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<List<ServiceGarmentPricingDTO>> getPricingGrid(@PathVariable UUID businessId) {
        return ResponseEntity.ok(catalogService.getPricingGrid(businessId));
    }

    @PostMapping("/pricing")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<ServiceGarmentPricingDTO> setPricing(
            @PathVariable UUID businessId,
            @Valid @RequestBody SetPricingRequest request) {
        return ResponseEntity.ok(catalogService.setPricing(businessId, request));
    }

    @DeleteMapping("/pricing/{pricingId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> removePricing(
            @PathVariable UUID businessId,
            @PathVariable UUID pricingId) {
        catalogService.removePricing(pricingId);
        return ResponseEntity.ok(SuccessResponse.of("Pricing removed"));
    }

    @GetMapping("/pricing/lookup")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PricingLookupResult> lookupPrice(
            @PathVariable UUID businessId,
            @RequestParam UUID serviceTypeId,
            @RequestParam UUID garmentTypeId) {
        return ResponseEntity.ok(catalogService.lookupPrice(businessId, serviceTypeId, garmentTypeId));
    }
}
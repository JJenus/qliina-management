package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.identity.dto.BusinessConfigDTO;
import com.jjenus.qliina_management.identity.service.BusinessConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Business Configuration", description = "Business configuration management endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/config")
@RequiredArgsConstructor
public class BusinessConfigController {
    
    private final BusinessConfigService configService;
     
    @Operation(
        summary = "Get business configuration",
        description = "Get configuration settings for a business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved configuration"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<BusinessConfigDTO> getConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(configService.getConfig(businessId));
    }
    
    @Operation(
        summary = "Update business configuration",
        description = "Update configuration settings for a business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<BusinessConfigDTO> updateConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody BusinessConfigDTO config) {
        return ResponseEntity.ok(configService.updateConfig(businessId, config));
    }
    
    @Operation(
        summary = "Reset to defaults",
        description = "Reset business configuration to default values"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration reset successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reset")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> resetConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        configService.resetToDefaults(businessId);
        return ResponseEntity.ok(SuccessResponse.of("Configuration reset to defaults"));
    }
}
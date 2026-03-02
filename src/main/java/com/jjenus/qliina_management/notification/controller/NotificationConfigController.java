package com.jjenus.qliina_management.notification.controller;

import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.notification.dto.EmailConfigurationDTO;
import com.jjenus.qliina_management.notification.dto.PushConfigurationDTO;
import com.jjenus.qliina_management.notification.dto.SMSConfigurationDTO;
import com.jjenus.qliina_management.notification.service.config.EmailConfigService;
import com.jjenus.qliina_management.notification.service.config.SmsConfigService;
import com.jjenus.qliina_management.notification.service.config.PushConfigService;
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

@Tag(name = "Notification Configuration", description = "Endpoints for managing notification channel configurations")
@RestController
@RequestMapping("/api/v1/{businessId}/notifications/config")
@RequiredArgsConstructor
public class NotificationConfigController {
    
    private final EmailConfigService emailConfigService;
    private final SmsConfigService smsConfigService;
    private final PushConfigService pushConfigService;
    
    // ==================== Email Configuration ====================
    
    @Operation(
        summary = "Get email configuration",
        description = "Retrieve email configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved configuration"),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/email")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.view')")
    public ResponseEntity<EmailConfigurationDTO> getEmailConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(emailConfigService.getConfiguration(businessId));
    }
    
    @Operation(
        summary = "Configure email",
        description = "Set up or update email configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email configured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/email")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<EmailConfigurationDTO> configureEmail(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody EmailConfigurationDTO request) {
        return ResponseEntity.ok(emailConfigService.configureEmail(businessId, request));
    }
    
    @Operation(
        summary = "Test email configuration",
        description = "Send a test email to verify configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test email sent"),
        @ApiResponse(responseCode = "400", description = "Configuration invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/email/test")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<SuccessResponse> testEmailConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @RequestParam String testEmail) {
        emailConfigService.testConfiguration(businessId, testEmail);
        return ResponseEntity.ok(SuccessResponse.of("Test email sent successfully"));
    }
    
    // ==================== SMS Configuration ====================
    
    @Operation(
        summary = "Get SMS configuration",
        description = "Retrieve SMS configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved configuration"),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/sms")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.view')")
    public ResponseEntity<SMSConfigurationDTO> getSmsConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(smsConfigService.getConfiguration(businessId));
    }
    
    @Operation(
        summary = "Configure SMS",
        description = "Set up or update SMS configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "SMS configured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/sms")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<SMSConfigurationDTO> configureSms(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody SMSConfigurationDTO request) {
        return ResponseEntity.ok(smsConfigService.configureSms(businessId, request));
    }
    
    @Operation(
        summary = "Test SMS configuration",
        description = "Send a test SMS to verify configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test SMS sent"),
        @ApiResponse(responseCode = "400", description = "Configuration invalid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/sms/test")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<SuccessResponse> testSmsConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @RequestParam String testPhoneNumber) {
        smsConfigService.testConfiguration(businessId, testPhoneNumber);
        return ResponseEntity.ok(SuccessResponse.of("Test SMS sent successfully"));
    }
    
    // ==================== Push Configuration ====================
    
    @Operation(
        summary = "Get push configuration",
        description = "Retrieve push notification configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved configuration"),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/push")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.view')")
    public ResponseEntity<PushConfigurationDTO> getPushConfig(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(pushConfigService.getConfiguration(businessId));
    }
    
    @Operation(
        summary = "Configure push notifications",
        description = "Set up or update push notification configuration for the business"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Push configured successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/push")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'notification.config.manage')")
    public ResponseEntity<PushConfigurationDTO> configurePush(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody PushConfigurationDTO request) {
        return ResponseEntity.ok(pushConfigService.configurePush(businessId, request));
    }
}

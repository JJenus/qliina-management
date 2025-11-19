package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.identity.dto.*;
import com.jjenus.qliina_management.identity.service.AuthService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @Operation(
        summary = "Login user",
        description = "Authenticate user credentials and return JWT tokens. If 2FA is enabled, returns requires2FA flag."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully authenticated",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "423", description = "Account locked",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }
    
    @Operation(
        summary = "Refresh token",
        description = "Get new access token using a valid refresh token"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }
    
    @Operation(
        summary = "Logout user",
        description = "Invalidate refresh token and clear security context"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logged out successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(SuccessResponse.of("Logged out successfully"));
    }
    
    @Operation(
        summary = "Forgot password",
        description = "Request password reset token (always returns success to prevent user enumeration)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "If account exists, reset link will be sent"),
        @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<SuccessResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(SuccessResponse.of("If the account exists, a reset link will be sent"));
    }
    
    @Operation(
        summary = "Reset password",
        description = "Reset password using token received via email"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Password mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<SuccessResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(SuccessResponse.of("Password reset successfully"));
    }
    
    @Operation(
        summary = "Change password",
        description = "Change password for authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password changed successfully"),
        @ApiResponse(responseCode = "401", description = "Current password incorrect",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Password mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/change-password")
    public ResponseEntity<SuccessResponse> changePassword(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = getCurrentUserId(userDetails);
        authService.changePassword(userId, request);
        return ResponseEntity.ok(SuccessResponse.of("Password changed successfully"));
    }
    
    @Operation(
        summary = "Verify 2FA",
        description = "Verify 2FA code during login process"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA verified, tokens issued",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid 2FA code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verify2FA(@Valid @RequestBody Verify2FARequest request) {
        return ResponseEntity.ok(authService.verify2FA(request));
    }
    
    @Operation(
        summary = "Setup 2FA",
        description = "Setup two-factor authentication for user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA setup completed",
                    content = @Content(schema = @Schema(implementation = Setup2FAResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/setup-2fa")
    public ResponseEntity<Setup2FAResponse> setup2FA(@Valid @RequestBody Setup2FARequest request) {
        return ResponseEntity.ok(authService.setup2FA(request));
    }
    
    @Operation(
        summary = "Disable 2FA",
        description = "Disable two-factor authentication for user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA disabled successfully"),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/disable-2fa")
    public ResponseEntity<SuccessResponse> disable2FA(@Valid @RequestBody Disable2FARequest request) {
        authService.disable2FA(request);
        return ResponseEntity.ok(SuccessResponse.of("2FA disabled successfully"));
    }
    
    private UUID getCurrentUserId(UserDetails userDetails) {
        // In real implementation, fetch from user repository
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
} 
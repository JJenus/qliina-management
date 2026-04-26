package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.business.dto.BusinessRegistrationResponse;
import com.jjenus.qliina_management.business.dto.CreateBusinessRequest;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.identity.dto.*;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Authentication and session management endpoints.
 * All routes under /api/v1/auth/** are permit-all in SecurityConfig,
 * but the JWT filter still populates the security context when a valid
 * Bearer token is present — so GET /me is effectively authenticated.
 */
@Tag(name = "Authentication", description = "Authentication, registration, and session management")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService    authService;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: GET /api/v1/auth/me
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
        summary     = "Get current user",
        description = "Returns the authenticated user's profile, roles, and permissions. "
                    + "Requires a valid Bearer access token in the Authorization header.",
        security    = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user info",
            content = @Content(schema = @Schema(implementation = AuthResponse.UserInfo.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> me(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            // Guard: shouldn't reach here with a proper JWT, but fail fast and clearly.
            throw new UsernameNotFoundException("No authenticated user");
        }

        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(authService.getCurrentUserInfo(userId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Existing endpoints (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Register a new business",
               description = "Open self-registration. Atomically creates a Business, its first Shop, "
                           + "and a BUSINESS_OWNER user. Returns JWT tokens immediately.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Registered successfully",
            content = @Content(schema = @Schema(implementation = BusinessRegistrationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Username, email, phone, or shop code already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register-business")
    public ResponseEntity<BusinessRegistrationResponse> registerBusiness(
            @Valid @RequestBody CreateBusinessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerBusiness(request));
    }

    @Operation(summary = "Login",
               description = "Authenticate with username/email and password. "
                           + "If 2FA is enabled, requires2FA=true and tokens are withheld until POST /verify-2fa.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "423", description = "Account locked",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @Operation(summary = "Refresh token",
               description = "Exchange a valid refresh token for a new access/refresh pair (rotation).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens refreshed",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @Operation(summary = "Logout", description = "Revoke refresh token and clear security context.")
    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(SuccessResponse.of("Logged out successfully"));
    }

    @Operation(summary = "Forgot password",
               description = "Request a password-reset link. Always returns 200 to prevent user enumeration.")
    @PostMapping("/forgot-password")
    public ResponseEntity<SuccessResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(SuccessResponse.of("If the account exists, a reset link will be sent"));
    }

    @Operation(summary = "Reset password", description = "Reset password using the emailed token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password reset"),
        @ApiResponse(responseCode = "400", description = "Invalid token or password mismatch",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<SuccessResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(SuccessResponse.of("Password reset successfully"));
    }

    @Operation(summary = "Change password", description = "Change password for the authenticated user.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password changed"),
        @ApiResponse(responseCode = "401", description = "Current password incorrect",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/change-password")
    public ResponseEntity<SuccessResponse> changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(resolveUserId(userDetails), request);
        return ResponseEntity.ok(SuccessResponse.of("Password changed successfully"));
    }

    @Operation(summary = "Verify 2FA", description = "Submit TOTP code after login returned requires2FA=true.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "2FA verified — tokens issued",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid code",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify-2fa")
    public ResponseEntity<AuthResponse> verify2FA(@Valid @RequestBody Verify2FARequest request) {
        return ResponseEntity.ok(authService.verify2FA(request));
    }

    @Operation(summary = "Setup 2FA", description = "Initiate 2FA — returns TOTP secret and QR-code URL.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Setup initiated",
            content = @Content(schema = @Schema(implementation = Setup2FAResponse.class))),
        @ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/setup-2fa")
    public ResponseEntity<Setup2FAResponse> setup2FA(@Valid @RequestBody Setup2FARequest request) {
        return ResponseEntity.ok(authService.setup2FA(request));
    }

    @Operation(summary = "Disable 2FA")
    @PostMapping("/disable-2fa")
    public ResponseEntity<SuccessResponse> disable2FA(@Valid @RequestBody Disable2FARequest request) {
        authService.disable2FA(request);
        return ResponseEntity.ok(SuccessResponse.of("2FA disabled successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private UUID resolveUserId(UserDetails userDetails) {
        if (userDetails == null) throw new UsernameNotFoundException("No authenticated user");
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()))
                .getId();
    }
}

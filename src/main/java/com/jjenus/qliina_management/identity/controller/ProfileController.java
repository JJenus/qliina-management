package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.identity.dto.AuthResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cross-tenant user profile endpoints — not scoped to a specific business.
 * Requires an authenticated JWT; the /api/v1/auth/** public prefix is NOT used.
 */
@Tag(name = "User Profile", description = "Current-user profile endpoints")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileController {

    private final AuthService    authService;
    private final UserRepository userRepository;

    @Operation(
        summary     = "Get current user",
        description = "Returns the authenticated user's profile, roles and permissions. "
                    + "Requires a valid Bearer access token.",
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
            throw new UsernameNotFoundException("No authenticated user");
        }

        UUID userId = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()))
                .getId();

        return ResponseEntity.ok(authService.getCurrentUserInfo(userId));
    }
}

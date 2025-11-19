package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.identity.dto.*;
import com.jjenus.qliina_management.identity.service.UserService;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "User Management", description = "User and permission management endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @Operation(
        summary = "List users",
        description = "Get paginated list of users with optional search"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved users"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.view')")
    public ResponseEntity<PageResponse<UserSummaryDTO>> listUsers(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            
            @Parameter(description = "Search query (searches in name, email, phone)")
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(userService.listUsers(businessId, search, pageable));
    }
    
    @Operation(
        summary = "Get user",
        description = "Get detailed user information by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved user"),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.view')")
    public ResponseEntity<UserDetailDTO> getUser(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }
    
    @Operation(
        summary = "Create user",
        description = "Create a new user with roles, permissions, and shop assignments"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error or duplicate fields",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.create')")
    public ResponseEntity<UserDetailDTO> createUser(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(businessId, request));
    }
    
    @Operation(
        summary = "Update user",
        description = "Update an existing user's basic information"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User updated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{userId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<UserDetailDTO> updateUser(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }
    
    @Operation(
        summary = "Deactivate user",
        description = "Soft delete - deactivate a user account"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User deactivated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.delete')")
    public ResponseEntity<SuccessResponse> deactivateUser(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId) {
        userService.deactivateUser(userId);
        return ResponseEntity.ok(SuccessResponse.of("User deactivated successfully"));
    }
    
    @Operation(
        summary = "Activate user",
        description = "Reactivate a deactivated user account"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User activated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{userId}/activate")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<SuccessResponse> activateUser(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId) {
        userService.activateUser(userId);
        return ResponseEntity.ok(SuccessResponse.of("User activated successfully"));
    }
    
    @Operation(
        summary = "Get user permissions",
        description = "Get detailed permissions for a user (role-based and direct)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved permissions"),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.view')")
    public ResponseEntity<UserPermissionsDTO> getUserPermissions(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserPermissions(userId));
    }
    
    @Operation(
        summary = "Assign roles",
        description = "Assign roles to user (clears existing roles)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Roles assigned successfully"),
        @ApiResponse(responseCode = "404", description = "User or role not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<SuccessResponse> assignRoles(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            
            @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(userId, request);
        return ResponseEntity.ok(SuccessResponse.of("Roles assigned successfully"));
    }
    
    @Operation(
        summary = "Remove role",
        description = "Remove a specific role from user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Role removed successfully"),
        @ApiResponse(responseCode = "404", description = "User or role not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<SuccessResponse> removeRole(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            
            @Parameter(description = "Role ID", required = true)
            @PathVariable UUID roleId) {
        userService.removeRole(userId, roleId);
        return ResponseEntity.ok(SuccessResponse.of("Role removed successfully"));
    }
    
    @Operation(
        summary = "Grant permission",
        description = "Grant direct permission to user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Permission granted successfully"),
        @ApiResponse(responseCode = "404", description = "User or permission not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{userId}/permissions")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<SuccessResponse> grantPermission(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            
            @Valid @RequestBody GrantPermissionRequest request) {
        userService.grantPermission(userId, request);
        return ResponseEntity.ok(SuccessResponse.of("Permission granted successfully"));
    }
    
    @Operation(
        summary = "Revoke permission",
        description = "Revoke direct permission from user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Permission revoked successfully"),
        @ApiResponse(responseCode = "404", description = "User or permission not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}/permissions/{permissionId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<SuccessResponse> revokePermission(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            
            @Parameter(description = "Permission ID", required = true)
            @PathVariable UUID permissionId) {
        userService.revokePermission(userId, permissionId);
        return ResponseEntity.ok(SuccessResponse.of("Permission revoked successfully"));
    }
    
    @Operation(
        summary = "Get user shops",
        description = "Get user's assigned shops with roles and permissions"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved shop assignments"),
        @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}/shops")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.view')")
    public ResponseEntity<List<UserShopAssignmentDTO>> getUserShops(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserShopAssignments(userId));
    }
    
    @Operation(
        summary = "Assign shops",
        description = "Assign user to shops with roles"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Shops assigned successfully"),
        @ApiResponse(responseCode = "404", description = "User or shop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{userId}/shops")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<SuccessResponse> assignShops(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            
            @Valid @RequestBody AssignShopsRequest request) {
        userService.assignShops(userId, request);
        return ResponseEntity.ok(SuccessResponse.of("Shops assigned successfully"));
    }
    
    @Operation(
        summary = "Remove from shop",
        description = "Remove user from a specific shop"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User removed from shop successfully"),
        @ApiResponse(responseCode = "404", description = "User or shop not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{userId}/shops/{shopId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'user.update')")
    public ResponseEntity<SuccessResponse> removeFromShop(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID userId,
            
            @Parameter(description = "Shop ID", required = true)
            @PathVariable UUID shopId) {
        userService.removeFromShop(userId, shopId);
        return ResponseEntity.ok(SuccessResponse.of("User removed from shop successfully"));
    }
} 
package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.dto.UserSummaryDTO;
import com.jjenus.qliina_management.identity.model.AuthAccount;
import com.jjenus.qliina_management.identity.model.Role;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.model.UserRole;
import com.jjenus.qliina_management.identity.repository.AuthAccountRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Platform staff user management:
 *   GET  /api/v1/admin/platform-users                 — list platform staff
 *   POST /api/v1/admin/platform-users                 — invite a new platform user
 *   PATCH /api/v1/admin/platform-users/{id}/deactivate — deactivate platform user
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/platform-users")
@RequiredArgsConstructor
public class AdminPlatformUserController {

    private static final List<String> PLATFORM_ROLES = List.of(
            "SUPER_ADMIN", "PLATFORM_ADMIN", "SUPPORT_AGENT", "BILLING_ADMIN", "READONLY_AUDITOR"
    );

    private static final List<String> INVITABLE_ROLES = List.of(
            "PLATFORM_ADMIN", "SUPPORT_AGENT", "BILLING_ADMIN", "READONLY_AUDITOR"
    );

    private final UserRepository     userRepository;
    private final RoleRepository     roleRepository;
    private final AuthAccountRepository authAccountRepository;
    private final PasswordEncoder    passwordEncoder;

    // -----------------------------------------------------------------------
    // List platform staff
    // -----------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.manage')")
    public PageResponse<UserSummaryDTO> listPlatformUsers(
            @RequestParam(required = false) String role,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<User> page;
        if (role != null && !role.isBlank()) {
            if (!PLATFORM_ROLES.contains(role)) {
                throw new BusinessException("Invalid role filter", "INVALID_ROLE");
            }
            page = userRepository.findByRoleName(role, pageable);
        } else {
            page = userRepository.findByAnyRoleName(PLATFORM_ROLES, pageable);
        }

        return PageResponse.from(page.map(this::toSummary));
    }

    // -----------------------------------------------------------------------
    // Invite / create a new platform user
    // -----------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.manage')")
    public ResponseEntity<UserSummaryDTO> invitePlatformUser(
            @Valid @RequestBody InvitePlatformUserRequest request) {

        if (!INVITABLE_ROLES.contains(request.getRole())) {
            throw new BusinessException(
                    "Role '" + request.getRole() + "' cannot be assigned via this endpoint",
                    "INVALID_ROLE", "role");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists", "USERNAME_EXISTS", "username");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists", "EMAIL_EXISTS", "email");
        }

        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new BusinessException("Role '" + request.getRole() + "' not found. Ensure DataInitializer has run.", "ROLE_NOT_FOUND"));

        // Create user — platform users have no businessId
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        // Platform users need a phone only if backend requires it; set empty placeholder
        user.setPhone(request.getPhone() != null ? request.getPhone() : "");
        user.setEnabled(true);
        user.setBusinessId(null);
        user = userRepository.save(user);

        // Auth account with a temporary password (user must change on first login)
        String tempPassword = request.getTemporaryPassword() != null
                ? request.getTemporaryPassword()
                : UUID.randomUUID().toString().substring(0, 12) + "Aa1!";
        AuthAccount auth = new AuthAccount();
        auth.setUser(user);
        auth.setPasswordHash(passwordEncoder.encode(tempPassword));
        auth.setPasswordLastChanged(LocalDateTime.now());
        auth.setFailedAttempts(0);
        authAccountRepository.save(auth);

        // Assign platform role (no businessId, no shopId → GLOBAL_SCOPE)
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setBusinessId(null);
        userRole.setShopId(null);
        user.getRoles().add(userRole);
        userRepository.save(user);

        log.info("Platform user created: {} with role {}", user.getUsername(), request.getRole());

        UserSummaryDTO dto = toSummary(user);
        return ResponseEntity.ok(dto);
    }

    // -----------------------------------------------------------------------
    // Deactivate a platform user
    // -----------------------------------------------------------------------

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.businesses.manage')")
    public ResponseEntity<Void> deactivatePlatformUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));

        // Protect SUPER_ADMIN accounts from deactivation through this endpoint
        boolean isSuperAdmin = user.getRoles().stream()
                .anyMatch(ur -> "SUPER_ADMIN".equals(ur.getRole().getName()));
        if (isSuperAdmin) {
            throw new BusinessException("SUPER_ADMIN accounts cannot be deactivated via this endpoint",
                    "FORBIDDEN_OPERATION");
        }

        user.setEnabled(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private UserSummaryDTO toSummary(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(ur -> ur.getRole().getName())
                .toList();
        return UserSummaryDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(user.getEnabled())
                .roles(roleNames)
                .createdAt(user.getCreatedAt())
                .build();
    }

    // -----------------------------------------------------------------------
    // Request body
    // -----------------------------------------------------------------------

    @Data
    public static class InvitePlatformUserRequest {
        @NotBlank
        @Size(min = 3, max = 50)
        private String username;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String firstName;

        @NotBlank
        private String lastName;

        private String phone;

        @NotBlank
        private String role;  // PLATFORM_ADMIN | SUPPORT_AGENT | BILLING_ADMIN | READONLY_AUDITOR

        /** Optional. If omitted, a random temp password is generated. */
        private String temporaryPassword;
    }
}

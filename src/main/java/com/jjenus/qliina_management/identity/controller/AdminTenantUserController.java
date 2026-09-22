package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Platform-staff actions on <b>tenant</b> users (N-4): deactivate / reactivate /
 * force password reset. Every action is permission-gated by
 * {@code platform.users.manage}, scoped to {@code businessId}, audited by AuditAspect,
 * and carries the same guards as the tenant-side equivalents (cannot deactivate the
 * last active BUSINESS_ADMIN). Distinct from {@link AdminPlatformUserController}
 * (platform staff accounts) and the read-only user list in
 * {@link AdminSupportController}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/businesses/{businessId}/users")
@RequiredArgsConstructor
public class AdminTenantUserController {

    private final UserService     userService;
    private final UserRepository  userRepository;

    @PatchMapping("/{userId}/deactivate")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.users.manage')")
    public ResponseEntity<Map<String, Object>> deactivate(
            @PathVariable UUID businessId,
            @PathVariable UUID userId,
            Authentication auth) {
        rejectSelf(businessId, userId, auth);
        userService.platformDeactivateUser(businessId, userId);
        log.info("Platform staff {} deactivated tenant user {} of business {}",
                auth.getName(), userId, businessId);
        return ResponseEntity.ok(Map.of("userId", userId, "enabled", false));
    }

    @PatchMapping("/{userId}/reactivate")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.users.manage')")
    public ResponseEntity<Map<String, Object>> reactivate(
            @PathVariable UUID businessId,
            @PathVariable UUID userId,
            Authentication auth) {
        userService.platformActivateUser(businessId, userId);
        log.info("Platform staff {} reactivated tenant user {} of business {}",
                auth.getName(), userId, businessId);
        return ResponseEntity.ok(Map.of("userId", userId, "enabled", true));
    }

    @PostMapping("/{userId}/force-password-reset")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.users.manage')")
    public ResponseEntity<Map<String, Object>> forcePasswordReset(
            @PathVariable UUID businessId,
            @PathVariable UUID userId,
            Authentication auth) {
        Map<String, Object> result = userService.platformForcePasswordReset(businessId, userId);
        log.info("Platform staff {} forced password reset for tenant user {} of business {}",
                auth.getName(), userId, businessId);
        return ResponseEntity.ok(result);
    }

    /** A platform admin must not disable the account they are acting through. */
    private void rejectSelf(UUID businessId, UUID userId, Authentication auth) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        if (auth.getName().equals(target.getUsername())) {
            throw new BusinessException("Cannot deactivate your own account", "SELF_DEACTIVATE", "userId");
        }
    }
}
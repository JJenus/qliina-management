// CustomPermissionEvaluator.java - Replace the whole file

package com.jjenus.qliina_management.identity.security;

import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {
    
    private final UserRepository userRepository;
    
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return false;
        }
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        
        return userRepository.findByUsername(username)
            .map(user -> hasPermission(user, permission.toString(), null, null))
            .orElse(false);
    }
    
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            log.debug("Authentication null or not UserDetails");
            return false;
        }
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        
        UUID businessId = null;
        UUID shopId = null;
        
        if (targetId != null && "BUSINESS".equals(targetType)) {
            businessId = UUID.fromString(targetId.toString());
        }
        if (targetId != null && "SHOP".equals(targetType)) {
            shopId = UUID.fromString(targetId.toString());
        }
        
        UUID finalBusinessId = businessId;
        UUID finalShopId = shopId;
        
        return userRepository.findByUsername(username)
            .map(user -> {
                boolean result = hasPermission(user, permission.toString(), finalBusinessId, finalShopId);
                log.debug("Permission check: user={}, permission={}, businessId={}, shopId={}, result={}", 
                    username, permission, finalBusinessId, finalShopId, result);
                return result;
            })
            .orElse(false);
    }
    
    private boolean hasPermission(User user, String permission, UUID businessId, UUID shopId) {
        log.debug("Checking permission {} for user {} (businessId={}, shopId={})", 
            permission, user.getUsername(), businessId, shopId);
        
        // First check if user has the permission through roles
        for (var userRole : user.getRoles()) {
            var role = userRole.getRole();
            
            // Check if role has this permission
            boolean roleHasPermission = role.getPermissions().stream()
                .anyMatch(p -> p.getName().equals(permission));
            
            if (!roleHasPermission) {
                continue;
            }
            
            log.debug("Role {} has permission {}", role.getName(), permission);
            
            UUID roleBusinessId = userRole.getBusinessId();
            UUID roleShopId = userRole.getShopId();
            
            // If no specific business is being checked (e.g., dashboard listing all businesses)
            if (businessId == null) {
                // Business-level role OR any role with matching business OR role with null businessId
                if (roleShopId == null) {
                    log.debug("Granted: business-level role, shopId null");
                    return true;
                }
                continue;
            }
            
            // Business ID validation - if roleBusinessId is null, treat as platform level
            if (roleBusinessId != null && !roleBusinessId.equals(businessId)) {
                log.debug("Business mismatch: roleBusinessId={}, requestBusinessId={}", roleBusinessId, businessId);
                continue;
            }
            
            // Shop validation
            if (shopId == null) {
                // Business-level permission (shopId not specified in annotation)
                if (roleShopId == null) {
                    log.debug("Granted: business-level permission");
                    return true;
                }
                // Role is shop-specific but we're checking business-level - still allow if business matches
                log.debug("Granted: business-level check, role has shop but business matches");
                return true;
            } else {
                // Shop-specific permission
                if (roleShopId == null) {
                    // Business-level role can access shop-level resources
                    log.debug("Granted: business-level role accessing shop resource");
                    return true;
                }
                if (roleShopId.equals(shopId)) {
                    log.debug("Granted: shop-specific role matching shop");
                    return true;
                }
            }
        }
        
        // Check direct permissions (if any)
        for (var userPermission : user.getDirectPermissions()) {
            if (!userPermission.getPermission().getName().equals(permission)) {
                continue;
            }
            
            // Check business ID
            UUID permBusinessId = userPermission.getBusinessId();
            if (permBusinessId != null && businessId != null && !permBusinessId.equals(businessId)) {
                continue;
            }
            
            // Check shop ID
            UUID permShopId = userPermission.getShopId();
            if (permShopId != null && shopId != null && !permShopId.equals(shopId)) {
                continue;
            }
            
            // Check expiry
            if (userPermission.getExpiresAt() != null && 
                userPermission.getExpiresAt().isBefore(LocalDateTime.now())) {
                continue;
            }
            
            log.debug("Granted: direct permission");
            return true;
        }
        
        log.debug("Permission {} DENIED for user {}", permission, user.getUsername());
        return false;
    }
}
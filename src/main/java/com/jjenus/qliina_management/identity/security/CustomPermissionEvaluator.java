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
            return false;
        }
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        
        UUID shopId = null;
        if (targetId != null && "SHOP".equals(targetType)) {
            shopId = UUID.fromString(targetId.toString());
        }
        
        UUID businessId = null;
        if (targetId != null && "BUSINESS".equals(targetType)) {
            businessId = UUID.fromString(targetId.toString());
        }
        
        UUID finalBusinessId = businessId;
        UUID finalShopId = shopId;
        
        return userRepository.findByUsername(username)
            .map(user -> hasPermission(user, permission.toString(), finalBusinessId, finalShopId))
            .orElse(false);
    }
    
    private boolean hasPermission(User user, String permission, UUID businessId, UUID shopId) {
        // Check if user has permission through roles
        boolean hasRolePermission = user.getRoles().stream()
            .anyMatch(userRole -> 
                userRole.getRole().getPermissions().stream()
                    .anyMatch(p -> p.getName().equals(permission)) &&
                (businessId == null || businessId.equals(userRole.getBusinessId())) &&
                (shopId == null || shopId.equals(userRole.getShopId()) || userRole.getShopId() == null)
            );
        
        if (hasRolePermission) {
            return true;
        }
        
        // Check direct permissions
        return user.getDirectPermissions().stream()
            .anyMatch(userPermission ->
                userPermission.getPermission().getName().equals(permission) &&
                (businessId == null || businessId.equals(userPermission.getBusinessId())) &&
                (shopId == null || shopId.equals(userPermission.getShopId()) || userPermission.getShopId() == null) &&
                (userPermission.getExpiresAt() == null || 
                 userPermission.getExpiresAt().isAfter(LocalDateTime.now()))
            );
    }
}

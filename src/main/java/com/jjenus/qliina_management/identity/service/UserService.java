package com.jjenus.qliina_management.identity.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.dto.*;
import com.jjenus.qliina_management.identity.model.*;
import com.jjenus.qliina_management.identity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;
    
    private UUID getCurrentUserId() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryDTO> listUsers(UUID businessId, String search, Pageable pageable) {
        Page<User> page;
        if (search != null && !search.trim().isEmpty()) {
            page = userRepository.searchUsers(businessId, search, pageable);
        } else {
            page = userRepository.findByBusinessId(businessId, pageable);
        }
        
        return PageResponse.from(page.map(this::mapToSummaryDTO));
    }
    
    @Transactional(readOnly = true)
    public UserDetailDTO getUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        return mapToDetailDTO(user);
    }
    
    @Transactional
    public UserDetailDTO createUser(UUID businessId, CreateUserRequest request) {
        // Validate unique fields
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists", "USERNAME_EXISTS", "username");
        }
        
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists", "EMAIL_EXISTS", "email");
        }
        
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("Phone number already exists", "PHONE_EXISTS", "phone");
        }
        
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match", "PASSWORD_MISMATCH", "confirmPassword");
        }
        
        // Create user
        User user = new User();
        user.setBusinessId(businessId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEnabled(true);
        
        if (request.getPrimaryShopId() != null) {
            Shop shop = shopRepository.findById(request.getPrimaryShopId())
                .orElseThrow(() -> new BusinessException("Shop not found", "SHOP_NOT_FOUND"));
            user.setPrimaryShopId(shop.getId());
        }
        
        user = userRepository.save(user);
        
        // Create auth account
        AuthAccount authAccount = new AuthAccount();
        authAccount.setUser(user);
        authAccount.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        authAccount.setPasswordLastChanged(LocalDateTime.now());
        authAccount.setFailedAttempts(0);
        authAccountRepository.save(authAccount);
        
        // Assign roles
        if (request.getRoles() != null) {
            for (CreateUserRequest.RoleAssignment assignment : request.getRoles()) {
                Role role = roleRepository.findById(assignment.getRoleId())
                    .orElseThrow(() -> new BusinessException("Role not found", "ROLE_NOT_FOUND"));
                
                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(role);
                userRole.setBusinessId(businessId);
                userRole.setShopId(assignment.getShopId());
                user.getRoles().add(userRole);
            }
        }
        
        // Assign direct permissions
        if (request.getDirectPermissions() != null) {
            for (CreateUserRequest.DirectPermission perm : request.getDirectPermissions()) {
                Permission permission = permissionRepository.findById(perm.getPermissionId())
                    .orElseThrow(() -> new BusinessException("Permission not found", "PERMISSION_NOT_FOUND"));
                
                UserPermission userPermission = new UserPermission();
                userPermission.setUser(user);
                userPermission.setPermission(permission);
                userPermission.setBusinessId(businessId);
                userPermission.setShopId(perm.getShopId());
                userPermission.setGrantedAt(LocalDateTime.now());
                userPermission.setGrantedBy(getCurrentUserId());
                userPermission.setExpiresAt(perm.getExpiresAt());
                user.getDirectPermissions().add(userPermission);
            }
        }
        
        // Assign shops
        if (request.getShopAssignments() != null) {
            Set<Shop> shops = new HashSet<>();
            for (UUID shopId : request.getShopAssignments()) {
                Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new BusinessException("Shop not found", "SHOP_NOT_FOUND"));
                shops.add(shop);
            }
            user.setShops(shops);
        }
        
        user = userRepository.save(user);
        
        return mapToDetailDTO(user);
    }
    
    @Transactional
    public UserDetailDTO updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        if (request.getEmail() != null) {
            if (!request.getEmail().equals(user.getEmail()) && 
                userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email already exists", "EMAIL_EXISTS", "email");
            }
            user.setEmail(request.getEmail());
        }
        
        if (request.getPhone() != null) {
            if (!request.getPhone().equals(user.getPhone()) && 
                userRepository.existsByPhone(request.getPhone())) {
                throw new BusinessException("Phone number already exists", "PHONE_EXISTS", "phone");
            }
            user.setPhone(request.getPhone());
        }
        
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        
        if (request.getPrimaryShopId() != null) {
            Shop shop = shopRepository.findById(request.getPrimaryShopId())
                .orElseThrow(() -> new BusinessException("Shop not found", "SHOP_NOT_FOUND"));
            user.setPrimaryShopId(shop.getId());
        }
        
        user = userRepository.save(user);
        
        return mapToDetailDTO(user);
    }
    
    @Transactional
    public void deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        user.setEnabled(false);
        userRepository.save(user);
    }
    
    @Transactional
    public void activateUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        user.setEnabled(true);
        userRepository.save(user);
    }
    
    @Transactional(readOnly = true)
    public UserPermissionsDTO getUserPermissions(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        List<UserPermissionsDTO.RolePermissionDTO> rolePermissions = user.getRoles().stream()
            .map(userRole -> UserPermissionsDTO.RolePermissionDTO.builder()
                .role(userRole.getRole().getName())
                .permissions(userRole.getRole().getPermissions().stream()
                    .map(Permission::getName)
                    .collect(Collectors.toList()))
                .build())
            .collect(Collectors.toList());
        
        List<String> directPermissions = user.getDirectPermissions().stream()
            .map(userPermission -> userPermission.getPermission().getName())
            .collect(Collectors.toList());
        
        List<String> effectivePermissions = rolePermissions.stream()
            .flatMap(rp -> rp.getPermissions().stream())
            .collect(Collectors.toList());
        effectivePermissions.addAll(directPermissions);
        
        List<UserPermissionsDTO.PermissionDetailDTO> permissionDetails = user.getRoles().stream()
            .flatMap(userRole -> userRole.getRole().getPermissions().stream()
                .map(permission -> UserPermissionsDTO.PermissionDetailDTO.builder()
                    .name(permission.getName())
                    .source("ROLE")
                    .sourceId(userRole.getRole().getId())
                    .scope(userRole.getShopId() != null ? "SHOP" : "BUSINESS")
                    .shopId(userRole.getShopId())
                    .build()))
            .collect(Collectors.toList());
        
        user.getDirectPermissions().forEach(userPermission -> 
            permissionDetails.add(UserPermissionsDTO.PermissionDetailDTO.builder()
                .name(userPermission.getPermission().getName())
                .source("DIRECT")
                .sourceId(userPermission.getPermission().getId())
                .scope(userPermission.getShopId() != null ? "SHOP" : "BUSINESS")
                .shopId(userPermission.getShopId())
                .build())
        );
        
        return UserPermissionsDTO.builder()
            .userId(user.getId())
            .businessId(user.getBusinessId())
            .rolePermissions(rolePermissions)
            .directPermissions(directPermissions)
            .effectivePermissions(effectivePermissions.stream().distinct().collect(Collectors.toList()))
            .permissionDetails(permissionDetails)
            .build();
    }
    
    @Transactional
    public void assignRoles(UUID userId, AssignRolesRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        // Clear existing roles
        user.getRoles().clear();
        
        // Assign new roles
        for (AssignRolesRequest.RoleAssignment assignment : request.getRoles()) {
            Role role = roleRepository.findById(assignment.getRoleId())
                .orElseThrow(() -> new BusinessException("Role not found", "ROLE_NOT_FOUND"));
            
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setBusinessId(user.getBusinessId());
            userRole.setShopId(assignment.getShopId());
            user.getRoles().add(userRole);
        }
        
        userRepository.save(user);
    }
    
    @Transactional
    public void removeRole(UUID userId, UUID roleId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        user.getRoles().removeIf(userRole -> userRole.getRole().getId().equals(roleId));
        userRepository.save(user);
    }
    
    @Transactional
    public void grantPermission(UUID userId, GrantPermissionRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        Permission permission = permissionRepository.findById(request.getPermissionId())
            .orElseThrow(() -> new BusinessException("Permission not found", "PERMISSION_NOT_FOUND"));
        
        UserPermission userPermission = new UserPermission();
        userPermission.setUser(user);
        userPermission.setPermission(permission);
        userPermission.setBusinessId(user.getBusinessId());
        userPermission.setShopId(request.getShopId());
        userPermission.setGrantedAt(LocalDateTime.now());
        userPermission.setGrantedBy(getCurrentUserId());
        userPermission.setExpiresAt(request.getExpiresAt());
        
        user.getDirectPermissions().add(userPermission);
        userRepository.save(user);
    }
    
    @Transactional
    public void revokePermission(UUID userId, UUID permissionId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        user.getDirectPermissions().removeIf(up -> up.getPermission().getId().equals(permissionId));
        userRepository.save(user);
    }
    
    @Transactional(readOnly = true)
    public List<UserShopAssignmentDTO> getUserShopAssignments(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        return user.getShops().stream()
            .map(shop -> {
                List<UserShopAssignmentDTO.RoleInfo> roles = user.getRoles().stream()
                    .filter(userRole -> shop.getId().equals(userRole.getShopId()))
                    .map(userRole -> UserShopAssignmentDTO.RoleInfo.builder()
                        .roleId(userRole.getRole().getId())
                        .roleName(userRole.getRole().getName())
                        .assignedAt(userRole.getCreatedAt())
                        .build())
                    .collect(Collectors.toList());
                
                List<UserShopAssignmentDTO.PermissionInfo> permissions = user.getDirectPermissions().stream()
                    .filter(userPermission -> shop.getId().equals(userPermission.getShopId()))
                    .map(userPermission -> UserShopAssignmentDTO.PermissionInfo.builder()
                        .permissionId(userPermission.getPermission().getId())
                        .permissionName(userPermission.getPermission().getName())
                        .source("DIRECT")
                        .grantedAt(userPermission.getGrantedAt())
                        .build())
                    .collect(Collectors.toList());
                
                // Add role-based permissions
                user.getRoles().stream()
                    .filter(userRole -> shop.getId().equals(userRole.getShopId()))
                    .forEach(userRole -> 
                        userRole.getRole().getPermissions().forEach(permission ->
                            permissions.add(UserShopAssignmentDTO.PermissionInfo.builder()
                                .permissionId(permission.getId())
                                .permissionName(permission.getName())
                                .source("ROLE")
                                .grantedAt(userRole.getCreatedAt())
                                .build())
                        )
                    );
                
                return UserShopAssignmentDTO.builder()
                    .shopId(shop.getId())
                    .shopName(shop.getName())
                    .isPrimary(shop.getId().equals(user.getPrimaryShopId()))
                    .roles(roles)
                    .permissions(permissions)
                    .build();
            })
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void assignShops(UUID userId, AssignShopsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        Set<Shop> shops = new HashSet<>();
        for (AssignShopsRequest.ShopAssignment assignment : request.getShopAssignments()) {
            Shop shop = shopRepository.findById(assignment.getShopId())
                .orElseThrow(() -> new BusinessException("Shop not found", "SHOP_NOT_FOUND"));
            shops.add(shop);
            
            if (assignment.getIsPrimary()) {
                user.setPrimaryShopId(shop.getId());
            }
            
            // Assign roles for this shop
            if (assignment.getRoles() != null) {
                for (UUID roleId : assignment.getRoles()) {
                    Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new BusinessException("Role not found", "ROLE_NOT_FOUND"));
                    
                    UserRole userRole = new UserRole();
                    userRole.setUser(user);
                    userRole.setRole(role);
                    userRole.setBusinessId(user.getBusinessId());
                    userRole.setShopId(shop.getId());
                    user.getRoles().add(userRole);
                }
            }
        }
        
        user.setShops(shops);
        userRepository.save(user);
    }
    
    @Transactional
    public void removeFromShop(UUID userId, UUID shopId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND"));
        
        user.getShops().removeIf(shop -> shop.getId().equals(shopId));
        
        // Remove shop-specific roles
        user.getRoles().removeIf(userRole -> shopId.equals(userRole.getShopId()));
        
        // Remove shop-specific permissions
        user.getDirectPermissions().removeIf(perm -> shopId.equals(perm.getShopId()));
        
        if (shopId.equals(user.getPrimaryShopId())) {
            user.setPrimaryShopId(null);
        }
        
        userRepository.save(user);
    }
    
    private UserSummaryDTO mapToSummaryDTO(User user) {
        return UserSummaryDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .phone(user.getPhone())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .enabled(user.getEnabled())
            .roles(user.getRoles().stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toList()))
            .primaryShop(user.getPrimaryShopId() != null ? 
                shopRepository.findById(user.getPrimaryShopId()).map(Shop::getName).orElse(null) : null)
            .lastLogin(user.getLastLogin())
            .createdAt(user.getCreatedAt())
            .build();
    }
    
    private UserDetailDTO mapToDetailDTO(User user) {
        List<UserDetailDTO.ShopAssignmentDTO> assignedShops = user.getShops().stream()
            .map(shop -> UserDetailDTO.ShopAssignmentDTO.builder()
                .shopId(shop.getId())
                .shopName(shop.getName())
                .isPrimary(shop.getId().equals(user.getPrimaryShopId()))
                .roles(user.getRoles().stream()
                    .filter(userRole -> shop.getId().equals(userRole.getShopId()))
                    .map(userRole -> userRole.getRole().getName())
                    .collect(Collectors.toList()))
                .build())
            .collect(Collectors.toList());
        
        List<UserDetailDTO.DirectPermissionDTO> directPermissions = user.getDirectPermissions().stream()
            .map(userPermission -> UserDetailDTO.DirectPermissionDTO.builder()
                .permissionId(userPermission.getPermission().getId())
                .permissionName(userPermission.getPermission().getName())
                .grantedAt(userPermission.getGrantedAt())
                .grantedBy(userPermission.getGrantedBy() != null ? 
                    userPermission.getGrantedBy().toString() : null)
                .expiresAt(userPermission.getExpiresAt())
                .build())
            .collect(Collectors.toList());
        
        UserDetailDTO.MetadataDTO metadata = UserDetailDTO.MetadataDTO.builder()
            .createdBy(user.getCreatedBy())
            .createdAt(user.getCreatedAt())
            .updatedBy(user.getUpdatedBy())
            .updatedAt(user.getUpdatedAt())
            .failedLoginAttempts(user.getAuthAccount() != null ? 
                user.getAuthAccount().getFailedAttempts() : 0)
            .build();
        
        UserDetailDTO dto = UserDetailDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .phone(user.getPhone())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .enabled(user.getEnabled())
            .roles(user.getRoles().stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toList()))
            .primaryShop(user.getPrimaryShopId() != null ? 
                shopRepository.findById(user.getPrimaryShopId()).map(Shop::getName).orElse(null) : null)
            .lastLogin(user.getLastLogin())
            .createdAt(user.getCreatedAt())
            .businessId(user.getBusinessId())
            .assignedShops(assignedShops)
            .directPermissions(directPermissions)
            .metadata(metadata)
            .build();
        
        return dto;
    }
}

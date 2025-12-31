package com.jjenus.qliina_management.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreateUserRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phone;
    
    @NotBlank(message = "First name is required")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    private String lastName;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    
    private String confirmPassword;
    
    private List<RoleAssignment> roles;
    
    private List<DirectPermission> directPermissions;
    
    private List<UUID> shopAssignments;
    
    private UUID primaryShopId;
    
    private Boolean sendInviteEmail = false;
    
    private Boolean notifyViaSms = false;
    
    @Data
    public static class RoleAssignment {
        private UUID roleId;
        private UUID shopId;
    }
    
    @Data
    public static class DirectPermission {
        private UUID permissionId;
        private UUID shopId;
        private LocalDateTime expiresAt;
    }
}

package com.jjenus.qliina_management.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDTO {
    private UUID id;
    private String username;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private List<String> roles;
    private String primaryShop;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
}

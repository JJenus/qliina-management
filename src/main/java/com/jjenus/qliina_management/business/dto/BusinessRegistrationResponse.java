package com.jjenus.qliina_management.business.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

/**
 * Response returned by POST /api/v1/auth/register-business.
 *
 * Mirrors the standard auth payload and adds businessId, businessSlug, and
 * shopId so the frontend can route to the correct tenant context immediately
 * without a second round-trip.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessRegistrationResponse {

    // Auth tokens
    private String  accessToken;
    private String  refreshToken;
    private String  tokenType;
    private Long    expiresIn;

    // Created entity references
    private UUID   businessId;
    private String businessSlug;
    private UUID   shopId;

    // Authenticated owner info
    private UserInfo user;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserInfo {
        private UUID         id;
        private String       username;
        private String       email;
        private String       phone;
        private String       firstName;
        private String       lastName;
        private UUID         businessId;
        private UUID         primaryShopId;
        private List<String> roles;
        private List<String> permissions;
    }
}

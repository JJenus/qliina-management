package com.jjenus.qliina_management.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }

            // "anonymousUser" is Spring Security's sentinel for unauthenticated requests
            if ("anonymousUser".equals(authentication.getName())) {
                return Optional.empty();
            }

            // JwtAuthenticationFilter stores the UUID from the "userId" JWT claim here
            Object details = authentication.getDetails();
            if (details instanceof UUID) {
                return Optional.of((UUID) details);
            }

            // details is absent or not a UUID (e.g. during the login request itself,
            // which is processed by AuthController before any token exists).
            // Return empty — Spring Data will leave @CreatedBy / @LastModifiedBy null,
            // which is the correct behaviour for the initial unauthenticated call.
            log.debug("getCurrentAuditor: no UUID in Authentication details for principal '{}'",
                    authentication.getName());
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error getting current auditor: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}

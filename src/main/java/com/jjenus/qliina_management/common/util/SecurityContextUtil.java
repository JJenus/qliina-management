package com.jjenus.qliina_management.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import com.jjenus.qliina_management.common.BusinessException;

import java.util.Optional;
import java.util.UUID;

public final class SecurityContextUtil {

    private SecurityContextUtil() {}

    public static Optional<Authentication> getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getName())) {
            return Optional.empty();
        }
        return Optional.of(auth);
    }

    /**
     * Returns the current user's UUID, or empty if not authenticated.
     */
    public static Optional<UUID> getCurrentUserId() {
        return getAuthentication().map(auth -> {
            Object details = auth.getDetails();
            if (details instanceof UUID) {
                return (UUID) details;
            }
            // Fallback: details is a String UUID (e.g. during tests)
            if (details instanceof String) {
                try { return UUID.fromString((String) details); }
                catch (IllegalArgumentException ignored) {}
            }
            return null;
        });
    }

    /**
     * Returns the current user's UUID or throws BusinessException.
     * Use in controllers / services where an unauthenticated call is a bug.
     */
    public static UUID requireUserId() {
        return getCurrentUserId().orElseThrow(() ->
            new BusinessException(
                "Cannot resolve current user -- ensure JWT contains userId claim",
                "USER_RESOLUTION_FAILED"));
    }

    // ------------------------------------------------------------------
    // Username (principal name from UserDetails)
    // ------------------------------------------------------------------

    public static Optional<String> getCurrentUsername() {
        return getAuthentication().map(Authentication::getName);
    }

    public static String requireUsername() {
        return getCurrentUsername().orElseThrow(() ->
            new BusinessException("Not authenticated", "NOT_AUTHENTICATED"));
    }

    // ------------------------------------------------------------------
    // Role / authority helpers
    // ------------------------------------------------------------------

    /**
     * Returns true if the current user has the given Spring authority string.
     * Example: hasRole("ROLE_ADMIN") or hasRole("notification.manage")
     */
    public static boolean hasRole(String authority) {
        return getAuthentication()
            .map(auth -> auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority)))
            .orElse(false);
    }

    // ------------------------------------------------------------------
    // UserDetails accessor (when the principal is a UserDetails object)
    // ------------------------------------------------------------------

    public static Optional<UserDetails> getCurrentUserDetails() {
        return getAuthentication()
            .filter(auth -> auth.getPrincipal() instanceof UserDetails)
            .map(auth -> (UserDetails) auth.getPrincipal());
    }
}

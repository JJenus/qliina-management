package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<UUID> {
    
    private final UserRepository userRepository;
    
    @Override
    public Optional<UUID> getCurrentAuditor() {
        try {
            // Get authentication from SecurityContext
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // If no authentication or not authenticated, return empty
            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("No authenticated user found for auditing");
                return Optional.empty();
            }
            
            // Get principal (could be UserDetails or String username)
            Object principal = authentication.getPrincipal();
            String username = null;
            
            if (principal instanceof UserDetails) {
                username = ((UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                username = (String) principal;
            }
            
            // If username is null or empty, return empty
            if (username == null || username.trim().isEmpty()) {
                log.debug("Username is null or empty for authenticated principal: {}", principal);
                return Optional.empty();
            }
            
            // Fetch user from database by username
            Optional<User> userOpt = userRepository.findByUsername(username);
            
            if (userOpt.isPresent()) {
                UUID userId = userOpt.get().getId();
                log.debug("Found auditor with ID: {}", userId);
                return Optional.of(userId);
            } else {
                log.warn("User not found in database for username: {}", username);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("Error getting current auditor: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
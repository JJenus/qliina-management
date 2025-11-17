package com.jjenus.qliina_management.identity.security;

import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        var authorities = user.getRoles().stream()
            .flatMap(userRole -> userRole.getRole().getPermissions().stream())
            .map(permission -> new SimpleGrantedAuthority(permission.getName()))
            .collect(Collectors.toSet());
        
        // Add direct permissions
        user.getDirectPermissions().stream()
            .filter(userPermission -> userPermission.getExpiresAt() == null || 
                                      userPermission.getExpiresAt().isAfter(LocalDateTime.now()))
            .map(userPermission -> new SimpleGrantedAuthority(userPermission.getPermission().getName()))
            .forEach(authorities::add);
        
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getAuthAccount().getPasswordHash())
            .authorities(authorities)
            .disabled(!user.getEnabled())
            .accountExpired(false)
            .accountLocked(isAccountLocked(user))
            .credentialsExpired(false)
            .build();
    }
    
    private boolean isAccountLocked(User user) {
        if (user.getAuthAccount() == null || user.getAuthAccount().getLockedUntil() == null) {
            return false;
        }
        return user.getAuthAccount().getLockedUntil().isAfter(LocalDateTime.now());
    }
}

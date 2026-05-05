package com.jjenus.qliina_management.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
      log.debug("=== Once Per Request Filter===");
        final String authHeader = request.getHeader("Authorization");
        
        boolean allow = shouldNotFilter(request);

        if (allow || authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            log.debug("No header or allowed route");
            log.debug("===End Once Per Request Filter===");
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String username = jwtProvider.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtProvider.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    Object userIdClaim = jwtProvider.extractClaim(jwt, claims -> claims.get("userId"));
                    if (userIdClaim != null) {
                        authToken.setDetails(UUID.fromString(userIdClaim.toString()));
                    }

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                
                log.debug("Authentication success");
            }

            log.debug("username: {}, auth context: {}", username, SecurityContextHolder.getContext().getAuthentication());
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }
        
        log.debug("===End Once Per Request Filter===");
        
        filterChain.doFilter(request, response);
    }

    @Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    
    if (contextPath != null && !contextPath.isEmpty()) {
        path = path.substring(contextPath.length());
    }
    
    // Debug logging
    log.debug("Checking if filter should apply for path: {}, servletPath: {}, requestURI: {}", 
              path, request.getServletPath(), request.getRequestURI());
    
    boolean shouldNotFilter = path.startsWith("/api/v1/auth/") ||
                              path.startsWith("/swagger-ui") ||
                              path.startsWith("/api-docs") ||
                              path.startsWith("/actuator/health");
    
    if (shouldNotFilter) {
        log.debug("Bypassing JWT filter for path: {}", path);
    }
    
    return shouldNotFilter;
}
}

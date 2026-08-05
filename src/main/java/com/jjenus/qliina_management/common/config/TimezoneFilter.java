package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.common.TimezoneContext;
import com.jjenus.qliina_management.identity.service.BusinessConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimezoneFilter extends OncePerRequestFilter {

    private static final String DEFAULT_TZ = "Africa/Lagos";

    private final BusinessConfigService configService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            UUID businessId = extractBusinessId(request);
            if (businessId != null) {
                try {
                    var config = configService.getConfig(businessId);
                    String tz = config.getTimezone();
                    if (tz != null) {
                        TimezoneContext.set(ZoneId.of(tz));
                    } else {
                        TimezoneContext.set(ZoneId.of(DEFAULT_TZ));
                    }
                } catch (Exception e) {
                    log.warn("Failed to resolve timezone for business {}, using fallback", businessId);
                    TimezoneContext.set(ZoneId.of(DEFAULT_TZ));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TimezoneContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    private UUID extractBusinessId(HttpServletRequest request) {
        String path = request.getRequestURI();
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if ("api".equals(segments[i]) && "v1".equals(segments[i + 1]) && i + 2 < segments.length) {
                try {
                    return UUID.fromString(segments[i + 2]);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }
}

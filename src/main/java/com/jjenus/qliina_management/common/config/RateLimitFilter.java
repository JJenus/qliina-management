package com.jjenus.qliina_management.common.config;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.qliina_management.common.config.RateLimitProperties.Limit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Whole-backend rate limiting keyed by client IP.
 *
 * <p>Every {@code /api/**} request consumes from a per-IP global budget;
 * {@code /api/v1/auth/**} additionally consumes from a stricter per-IP
 * login budget. Exceeded budgets return 429 ProblemDetail instead of
 * reaching the controller. Per-account brute-force protection (failed
 * attempts -> temporary lockout) lives in {@code AuthService}; this filter
 * adds the transport-level throttle on top of it.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json;charset=UTF-8";
    private static final String LOGIN_PREFIX = "/api/v1/auth/";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, Bucket> globalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);

        if (!tryConsume(globalBuckets, clientIp, properties.getDefaultLimit())) {
            writeTooManyRequests(request, response);
            return;
        }
        if (request.getRequestURI().startsWith(LOGIN_PREFIX)
                && !tryConsume(loginBuckets, clientIp, properties.getLogin())) {
            writeTooManyRequests(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryConsume(Map<String, Bucket> buckets, String key, Limit limit) {
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> createBucket(limit));
        return bucket.tryConsume(1);
    }

    private Bucket createBucket(Limit limit) {
        return Bucket4j.builder()
                .addLimit(Bandwidth.classic(limit.getCapacity(),
                        Refill.greedy(limit.getRefillPerMinute(), Duration.ofMinutes(1))))
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Mirrors the flattened RFC-7807 shape every controller error uses
        // (Boot serializes ProblemDetail "properties" at top level), so
        // clients can read $.errorCode the same way for 429 as for 4xx/5xx.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://api.laundry.com/errors/rate-limited");
        body.put("title", "Too Many Requests");
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("detail", "Too many requests. Please slow down and try again.");
        body.put("instance", request.getRequestURI());
        body.put("errorCode", "RATE_LIMITED");
        body.put("path", request.getRequestURI());
        body.put("timestamp", LocalDateTime.now());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(PROBLEM_MEDIA_TYPE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
package com.jjenus.qliina_management.identity.controller;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.identity.model.SystemSetting;
import com.jjenus.qliina_management.identity.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-global configuration:
 *   GET /api/v1/admin/system-settings — list settings (seeds defaults on first read)
 *   PUT /api/v1/admin/system-settings — bulk upsert {key: value, ...}
 *   GET /api/v1/admin/jobs            — scheduled-job registry + server heartbeat
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSystemController {

    /** key → (defaultValue, description) — seeded on first read if missing. */
    private static final Map<String, String[]> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("maintenance_mode",     new String[]{"false", "When true, tenant logins are blocked for maintenance"});
        DEFAULTS.put("signups_enabled",      new String[]{"true",  "Allow open business self-registration"});
        DEFAULTS.put("default_trial_days",   new String[]{"14",    "Trial length applied to new businesses"});
        DEFAULTS.put("default_currency",     new String[]{"NGN",   "Fallback currency for billing"});
        DEFAULTS.put("support_email",        new String[]{"support@qliina.com", "Shown to tenants for support requests"});
    }

    private final SystemSettingRepository settingRepository;

    // ---------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------

    @GetMapping("/system-settings")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.settings.manage')
        or hasPermission(null, 'PLATFORM', 'platform.businesses.view')
    """)
    public ResponseEntity<List<Map<String, Object>>> listSettings() {
        seedDefaults();
        List<Map<String, Object>> out = settingRepository.findAll().stream()
                .sorted((a, b) -> a.getSettingKey().compareTo(b.getSettingKey()))
                .map(s -> Map.<String, Object>of(
                        "key", s.getSettingKey(),
                        "value", s.getSettingValue() != null ? s.getSettingValue() : "",
                        "description", s.getDescription() != null ? s.getDescription() : "",
                        "updatedByUsername", s.getUpdatedByUsername() != null ? s.getUpdatedByUsername() : "",
                        "updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : ""))
                .toList();
        return ResponseEntity.ok(out);
    }

    @PutMapping("/system-settings")
    @PreAuthorize("hasPermission(null, 'PLATFORM', 'platform.settings.manage')")
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new BusinessException("Settings body is required", "VALIDATION_ERROR");
        }
        seedDefaults();
        String by = currentUsername();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            if (!DEFAULTS.containsKey(key) && !key.startsWith("flag.")) {
                throw new BusinessException("Unknown setting key: " + key, "UNKNOWN_SETTING", "key");
            }
            SystemSetting s = settingRepository.findBySettingKey(key).orElseGet(() -> {
                SystemSetting n = new SystemSetting();
                n.setSettingKey(key);
                return n;
            });
            s.setSettingValue(String.valueOf(e.getValue()));
            s.setDescription(s.getDescription() != null ? s.getDescription()
                    : DEFAULTS.getOrDefault(key, new String[]{"", "Global feature flag"})[1]);
            s.setUpdatedByUsername(by);
            settingRepository.save(s);
        }
        return ResponseEntity.ok(Map.of("updated", body.size(), "updatedBy", by));
    }

    // ---------------------------------------------------------------------
    // Scheduled-job registry (visibility for ops)
    // ---------------------------------------------------------------------

    @GetMapping("/jobs")
    @PreAuthorize("""
        hasPermission(null, 'PLATFORM', 'platform.settings.manage')
        or hasPermission(null, 'PLATFORM', 'platform.businesses.view')
    """)
    public ResponseEntity<Map<String, Object>> jobs() {
        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> jobs = List.of(
                job("billing-renewal-sweep",       "0 15 * * * *",  "Renews subscriptions whose period has lapsed"),
                job("billing-dunning-retry-sweep", "0 45 * * * *",  "Retries failed payments per dunning schedule"),
                job("billing-trial-expiry-sweep",  "0 30 * * * *",  "Expires trials and downgrades to FREE"),
                job("billing-cancellation-finalizer", "0 5 * * * *","Finalizes cancel-at-period-end subscriptions"),
                job("notification-outbox-worker",  "fixedDelay=2s", "Drains the transactional notification outbox"),
                job("notification-delivery-processor", "fixedDelay=5s", "Processes pending notification deliveries"),
                job("notification-retry-processor", "fixedDelay=30s", "Retries failed deliveries with backoff"),
                job("audit-retention-cleanup",     "0 0 2 * * *",   "Purges audit logs past retention window"),
                job("consent-cleanup",             "0 0 3 * * *",   "Consent record housekeeping"),
                job("dsr-reminders",               "0 0 9 * * *",   "Data-subject request reminders"),
                job("security-event-monitor",      "fixedDelay=5m", "Brute-force / suspicious activity monitor"),
                job("employee-idle-detection",     "fixedDelay=60s","Auto-suspends idle employee shifts"),
                job("employee-midnight-autoclose", "0 5 0 * * *",   "Closes shifts left open at midnight")
        );

        Runtime rt = Runtime.getRuntime();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("serverTime", now.toString());
        meta.put("javaUptimeMinutes", Duration.ofMillis(System.currentTimeMillis() - startTime()).toMinutes());
        meta.put("availableProcessors", rt.availableProcessors());
        meta.put("maxMemoryMb", rt.maxMemory() / (1024 * 1024));
        meta.put("usedMemoryMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobs", jobs);
        result.put("runtime", meta);
        return ResponseEntity.ok(result);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void seedDefaults() {
        for (Map.Entry<String, String[]> e : DEFAULTS.entrySet()) {
            if (settingRepository.findBySettingKey(e.getKey()).isEmpty()) {
                SystemSetting s = new SystemSetting();
                s.setSettingKey(e.getKey());
                s.setSettingValue(e.getValue()[0]);
                s.setDescription(e.getValue()[1]);
                s.setUpdatedByUsername("system");
                settingRepository.save(s);
            }
        }
    }

    private Map<String, Object> job(String name, String schedule, String purpose) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("schedule", schedule);
        m.put("purpose", purpose);
        return m;
    }

    private static long startTime() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }
}

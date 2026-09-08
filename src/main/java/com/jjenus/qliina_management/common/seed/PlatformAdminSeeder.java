package com.jjenus.qliina_management.common.seed;

import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.identity.model.AuthAccount;
import com.jjenus.qliina_management.identity.model.Role;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.model.UserRole;
import com.jjenus.qliina_management.identity.repository.AuthAccountRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Creates the Qliina platform business and the initial {@code admin} superadmin.
 *
 * This is the only credential-bearing seed, so it is separately gated:
 * {@code app.seed-data.admin.enabled} is default-on for dev/test but default-off
 * in prod (bootstrap must be explicit). The admin password comes from
 * {@code app.seed-data.admin.password} and the seeder fails fast rather than
 * ever fall back to a committed credential. It is create-only and idempotent.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-data.admin.enabled", havingValue = "true", matchIfMissing = true)
public class PlatformAdminSeeder implements CommandLineRunner {

    private static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final String PLATFORM_BUSINESS_SLUG = "qliina-platform";

    private final BusinessRepository    businessRepository;
    private final UserRepository        userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;

    @Value("${app.seed-data.admin.password:}")
    private String adminPassword;

    private UUID platformBusinessId;

    @Override
    @Transactional
    public void run(String... args) {
        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException(
                    "app.seed-data.admin.password is required when admin seeding runs — set SEED_ADMIN_PASSWORD "
                            + "(a committed default is never used in prod)");
        }
        log.info("Starting admin bootstrap...");
        createPlatformBusiness();
        createSuperAdmin();
        log.info("Admin bootstrap complete (platformBusinessId={}).", platformBusinessId);
    }

    private void createPlatformBusiness() {
        businessRepository.findBySlug(PLATFORM_BUSINESS_SLUG).ifPresentOrElse(
            existing -> {
                platformBusinessId = existing.getId();
                log.info("Platform business exists — skipping (id={}).", platformBusinessId);
            },
            () -> {
                log.info("Creating platform business...");
                Business platform = Business.builder()
                        .name("Qliina Platform")
                        .slug(PLATFORM_BUSINESS_SLUG)
                        .status(Business.Status.ACTIVE)
                        .plan(Business.Plan.ENTERPRISE)
                        .email("platform@qliina.com")
                        .build();
                platform.setCreatedAt(LocalDateTime.now());
                platform.setCreatedBy(SYSTEM_USER_ID);
                Business saved = businessRepository.save(platform);
                platformBusinessId = saved.getId();
                log.info("Platform business created (id={}).", platformBusinessId);
            }
        );
    }

    private void createSuperAdmin() {
        if (userRepository.findByUsername("admin").isPresent()) {
            log.info("Superadmin exists — skipping.");
            return;
        }
        log.info("Creating superadmin user...");
        LocalDateTime now = LocalDateTime.now();

        User admin = new User();
        admin.setUsername("admin"); 
        admin.setEmail("admin@qliina.com");
        admin.setPhone("+1234567890"); 
        admin.setFirstName("Super"); 
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.setBusinessId(platformBusinessId);
        admin.setCreatedAt(now); 
        admin.setCreatedBy(SYSTEM_USER_ID);
        userRepository.save(admin);

        AuthAccount auth = new AuthAccount();
        auth.setUser(admin); 
        auth.setPasswordHash(passwordEncoder.encode(adminPassword));
        auth.setPasswordLastChanged(now); 
        auth.setFailedAttempts(0); auth.setTotpEnabled(false);
        auth.setCreatedAt(now); 
        auth.setCreatedBy(SYSTEM_USER_ID);
        authAccountRepository.save(auth);

        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not found"));
        UserRole ur = new UserRole();
        ur.setUser(admin); 
        ur.setShopScope(null);
        ur.setRole(superAdminRole);
        ur.setBusinessId(platformBusinessId);
        ur.setCreatedAt(now); ur.setCreatedBy(SYSTEM_USER_ID);
        admin.getRoles().add(ur);
        userRepository.save(admin);

        log.info("Superadmin created: username=admin, businessId={}", platformBusinessId);
    }
}
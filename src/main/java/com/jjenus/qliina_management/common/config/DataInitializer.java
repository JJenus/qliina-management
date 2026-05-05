// ./src/main/java/com/jjenus/qliina_management/common/config/DataInitializer.java
package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.identity.model.*;
import com.jjenus.qliina_management.identity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository        roleRepository;
    private final PermissionRepository  permissionRepository;
    private final UserRepository        userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final BusinessRepository    businessRepository;
    private final PasswordEncoder       passwordEncoder;

    private static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final String PLATFORM_BUSINESS_SLUG = "qliina-platform";

    private UUID platformBusinessId;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting database initialization...");
        createPermissions();
        createRoles();
        syncRolePermissions();
        createPlatformBusiness();
        createSuperAdmin();
        log.info("Database initialization complete.");
    }

    // -------------------------------------------------------------------------

    private void createPermissions() {
        log.info("Ensuring permissions exist...");
        // Each call is idempotent — skips if name already exists

        // User management
        perm("user.view",   "View Users",   "View user details",          "USER_MANAGEMENT", "BUSINESS", true);
        perm("user.create", "Create Users", "Create new users",           "USER_MANAGEMENT", "BUSINESS", true);
        perm("user.update", "Update Users", "Update existing users",       "USER_MANAGEMENT", "BUSINESS", true);
        perm("user.delete", "Delete Users", "Delete or deactivate users",  "USER_MANAGEMENT", "BUSINESS", false);

        // Order management
        perm("order.view",          "View Orders",         "View order details",           "ORDER_MANAGEMENT", "SHOP", true);
        perm("order.create",        "Create Orders",       "Create new orders",            "ORDER_MANAGEMENT", "SHOP", true);
        perm("order.update",        "Update Orders",       "Update existing orders",       "ORDER_MANAGEMENT", "SHOP", true);
        perm("order.delete",        "Delete Orders",       "Delete or cancel orders",      "ORDER_MANAGEMENT", "SHOP", true);
        perm("order.status.update", "Update Order Status", "Change order status",          "ORDER_MANAGEMENT", "SHOP", true);
        perm("order.transfer",      "Transfer Orders",     "Transfer between shops",       "ORDER_MANAGEMENT", "SHOP", true);

        // Payment
        perm("payment.process", "Process Payments", "Process payments", "PAYMENT", "SHOP", true);
        perm("payment.refund",  "Process Refunds",  "Process refunds",  "PAYMENT", "SHOP", false);
        perm("payment.view",    "View Payments",    "View payments",    "PAYMENT", "SHOP", true);

        // Customer management
        perm("customer.view",   "View Customers",   "View customer details",     "CUSTOMER_MANAGEMENT", "BUSINESS", true);
        perm("customer.create", "Create Customers", "Create new customers",      "CUSTOMER_MANAGEMENT", "BUSINESS", true);
        perm("customer.update", "Update Customers", "Update customers",          "CUSTOMER_MANAGEMENT", "BUSINESS", true);
        perm("customer.delete", "Delete Customers", "Delete or deactivate",      "CUSTOMER_MANAGEMENT", "BUSINESS", false);

        // Reporting
        perm("report.view.financial",   "View Financial Reports",   "View financial reports",   "REPORTING", "BUSINESS", false);
        perm("report.view.operational", "View Operational Reports", "View operational reports", "REPORTING", "BUSINESS", true);
        perm("report.export",           "Export Reports",           "Export reports",           "REPORTING", "BUSINESS", false);

        // Inventory
        perm("inventory.view",   "View Inventory",   "View inventory items",           "INVENTORY", "SHOP", true);
        perm("inventory.manage", "Manage Inventory", "Create/update/delete inventory", "INVENTORY", "SHOP", false);
        perm("inventory.adjust", "Adjust Stock",     "Adjust stock levels",            "INVENTORY", "SHOP", true);

        // Quality control
        perm("quality.check",  "Perform QC Checks", "Perform QC on orders",  "QUALITY", "SHOP",     true);
        perm("quality.manage", "Manage Quality",    "Manage QC checklists",  "QUALITY", "BUSINESS", false);
        perm("quality.view",   "View QC Reports",   "View quality metrics",  "QUALITY", "BUSINESS", true);

        // Notifications
        perm("notification.view",   "View Notifications",    "View notifications",              "NOTIFICATION", "BUSINESS", true);
        perm("notification.send",   "Send Notifications",    "Send notifications to users",     "NOTIFICATION", "BUSINESS", false);
        perm("notification.update", "Update Notifications",  "Mark notifications as read",      "NOTIFICATION", "BUSINESS", true);
        perm("notification.manage", "Manage Notifications",  "Manage templates and settings",   "NOTIFICATION", "BUSINESS", false);

        // Employee management — ADDED
        perm("employee.view",   "View Employees",   "View employee details",           "EMPLOYEE", "BUSINESS", true);
        perm("employee.clock",  "Clock In/Out",     "Clock in and out of shifts",     "EMPLOYEE", "SHOP",     true);
        perm("employee.manage", "Manage Employees", "Manage schedules and attendance","EMPLOYEE", "BUSINESS", false);

        // Admin
        perm("admin.settings", "Manage Settings", "Manage business settings", "ADMIN", "BUSINESS", false);
        perm("admin.audit",    "View Audit Logs", "View system audit logs",   "ADMIN", "BUSINESS", false);

        log.info("Permissions check complete. Total: {}", permissionRepository.count());
    }

    /** Idempotent — creates only if name doesn't exist */
    private void perm(String name, String display, String desc, String cat, String scope, boolean def) {
        if (permissionRepository.findByName(name).isPresent()) return;
        Permission p = new Permission();
        p.setName(name); p.setDisplayName(display); p.setDescription(desc);
        p.setCategory(cat); p.setScope(Permission.PermissionScope.valueOf(scope)); p.setIsDefault(def);
        p.setCreatedAt(LocalDateTime.now()); p.setCreatedBy(SYSTEM_USER_ID);
        permissionRepository.save(p);
        log.debug("Created permission: {}", name);
    }

    private void createRoles() {
        if (roleRepository.count() > 0) { log.info("Roles exist — skipping."); return; }
        log.info("Creating default roles...");
        LocalDateTime now = LocalDateTime.now();

        role("SUPER_ADMIN",    "Platform super administrator — full access",           Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findAll()));
        role("BUSINESS_OWNER", "Business owner — full business control",               Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByScopeIn(Arrays.asList(
                     Permission.PermissionScope.BUSINESS, Permission.PermissionScope.SHOP))));
        role("SHOP_MANAGER",   "Shop manager — operational control of assigned shops", Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByScope(Permission.PermissionScope.SHOP)));
        role("FRONT_DESK",     "Front desk associate — order intake and payments",     Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "order.view","order.create","order.update","payment.process",
                     "report.view.operational",
                     "customer.view","customer.create","customer.update",
                     "notification.view","notification.update",
                     "report.view.operational",
                     "employee.clock","employee.view"))));
        role("WASHER",         "Laundry technician — washing and drying",              Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "order.view","order.status.update","quality.view","quality.check",
                     "notification.view","notification.update",
                     "employee.clock","employee.view"))));
        role("IRONER",         "Ironing and finishing staff",                          Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "order.view","order.status.update","quality.view","quality.check",
                     "notification.view","notification.update",
                     "employee.clock","employee.view"))));
        role("DELIVERY",       "Delivery personnel",                                   Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "order.view","order.status.update",
                     "notification.view","notification.update",
                     "employee.clock","employee.view"))));
        log.info("Created {} roles.", roleRepository.count());
    }

    private void role(String name, String desc, Role.RoleType type, boolean system,
                      LocalDateTime now, Set<Permission> perms) {
        Role r = new Role();
        r.setName(name); r.setDescription(desc); r.setType(type);
        r.setIsSystem(system); r.setPermissions(perms);
        r.setCreatedAt(now); r.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(r);
    }

    /**
     * Syncs permissions on existing roles every startup.
     * This ensures roles get any newly added permissions without manual intervention.
     */
    private void syncRolePermissions() {
        log.info("Syncing role permissions...");

        syncRole("SUPER_ADMIN",
            new HashSet<>(permissionRepository.findAll()));

        syncRole("BUSINESS_OWNER",
            new HashSet<>(permissionRepository.findByScopeIn(Arrays.asList(
                Permission.PermissionScope.BUSINESS, Permission.PermissionScope.SHOP))));

        syncRole("SHOP_MANAGER",
            new HashSet<>(permissionRepository.findByScope(Permission.PermissionScope.SHOP)));

        syncRole("FRONT_DESK",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "order.view","order.create","order.update","payment.process",
                "customer.view","customer.create","customer.update",
                "notification.view","notification.update",
                "employee.clock","employee.view"))));

        syncRole("WASHER",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "order.view","order.status.update","quality.view","quality.check",
                "notification.view","notification.update",
                "employee.clock","employee.view"))));

        syncRole("IRONER",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "order.view","order.status.update","quality.view","quality.check",
                "notification.view","notification.update",
                "employee.clock","employee.view"))));

        syncRole("DELIVERY",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "order.view","order.status.update",
                "notification.view","notification.update",
                "employee.clock","employee.view"))));
    }

    private void syncRole(String roleName, Set<Permission> permissions) {
        roleRepository.findByName(roleName).ifPresent(role -> {
            role.setPermissions(permissions);
            roleRepository.save(role);
            log.info("Synced {} permissions for role: {}", permissions.size(), roleName);
        });
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
        admin.setUsername("admin"); admin.setEmail("admin@qliina.com");
        admin.setPhone("+1234567890"); admin.setFirstName("Super"); admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.setBusinessId(platformBusinessId);
        admin.setCreatedAt(now); admin.setCreatedBy(SYSTEM_USER_ID);
        userRepository.save(admin);

        AuthAccount auth = new AuthAccount();
        auth.setUser(admin); auth.setPasswordHash(passwordEncoder.encode("Admin@123"));
        auth.setPasswordLastChanged(now); auth.setFailedAttempts(0); auth.setTotpEnabled(false);
        auth.setCreatedAt(now); auth.setCreatedBy(SYSTEM_USER_ID);
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
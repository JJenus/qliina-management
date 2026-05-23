package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.model.SubscriptionPlan;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.SubscriptionPlanRepository;
import com.jjenus.qliina_management.identity.model.*;
import com.jjenus.qliina_management.identity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository            roleRepository;
    private final PermissionRepository      permissionRepository;
    private final UserRepository            userRepository;
    private final AuthAccountRepository     authAccountRepository;
    private final BusinessRepository        businessRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder           passwordEncoder;

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
        seedSubscriptionPlans();
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

        // Expenses
        perm("expenses.manage", "Manage Expenses", "Create, update and delete expense records", "REPORTING", "BUSINESS", false);

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

        // Employee management
        perm("employee.view",   "View Employees",   "View employee details",           "EMPLOYEE", "BUSINESS", true);
        perm("employee.clock",  "Clock In/Out",     "Clock in and out of shifts",     "EMPLOYEE", "SHOP",     true);
        perm("employee.manage", "Manage Employees", "Manage schedules and attendance","EMPLOYEE", "BUSINESS", false);

        // Admin
        perm("admin.settings", "Manage Settings", "Manage business settings", "ADMIN", "BUSINESS", false);
        perm("admin.audit",    "View Audit Logs", "View system audit logs",   "ADMIN", "BUSINESS", false);

        // Platform roles (cross-tenant access for Qliina staff)
        perm("platform.businesses.view",   "View All Businesses",       "List and view all businesses",           "PLATFORM_ADMIN", "BUSINESS", false);
        perm("platform.businesses.manage", "Manage All Businesses",     "Update status and plan of businesses",   "PLATFORM_ADMIN", "BUSINESS", false);
        perm("platform.plans.manage",      "Manage Subscription Plans", "CRUD subscription plan definitions",     "PLATFORM_ADMIN", "BUSINESS", false);
        perm("platform.support.view",      "Support View",              "View operational data (masked PII)",     "PLATFORM_ADMIN", "BUSINESS", false);
        perm("platform.billing.manage",    "Manage Billing",            "Manage plan tiers and trial extensions", "PLATFORM_ADMIN", "BUSINESS", false);
        perm("platform.audit.view",        "Platform Audit View",       "Full read-only audit access",            "PLATFORM_ADMIN", "BUSINESS", false);

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

        // SUPER_ADMIN - Full platform access
        role("SUPER_ADMIN", "Platform super administrator — full access", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findAll()));

        // BUSINESS_OWNER - Full business control (all BUSINESS and SHOP scope permissions)
        role("BUSINESS_OWNER", "Business owner — full business control", Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByScopeIn(Arrays.asList(
                     Permission.PermissionScope.BUSINESS, Permission.PermissionScope.SHOP))));

        // SHOP_MANAGER - Operational control with necessary business permissions
        role("SHOP_MANAGER", "Shop manager — operational control of assigned shops", Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     // Order management (full control)
                     "order.view", "order.create", "order.update", "order.delete",
                     "order.status.update", "order.transfer",
                     // Payment management (full control)
                     "payment.process", "payment.refund", "payment.view",
                     // Inventory management (full control)
                     "inventory.view", "inventory.manage", "inventory.adjust",
                     // Quality control (shop-level checks)
                     "quality.check",
                     // Quality management (business-level quality oversight)
                     "quality.view", "quality.manage",
                     // Customer management (full control)
                     "customer.view", "customer.create", "customer.update",
                     // Reporting (all reports) + expense management
                     "report.view.operational", "report.view.financial", "report.export", "expenses.manage",
                     // Notifications (view and update)
                     "notification.view", "notification.update",
                     // Employee management (full control)
                     "employee.view", "employee.clock", "employee.manage",
                     // User management (can view users)
                     "user.view"
             ))));

        // FRONT_DESK - Order intake, payments, and customer service
        role("FRONT_DESK", "Front desk associate — order intake and payments", Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     // Order management (create and view)
                     "order.view", "order.create", "order.update", "order.status.update",
                     // Payment processing
                     "payment.process", "payment.view",
                     // Customer management
                     "customer.view", "customer.create", "customer.update",
                     // Reporting (operational dashboard)
                     "report.view.operational",
                     // Notifications
                     "notification.view", "notification.update",
                     // Employee (own clock-in and view)
                     "employee.clock", "employee.view"
             ))));

        // WASHER - Laundry technicians
        role("WASHER", "Laundry technician — washing and drying", Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     // Order management (view and status updates)
                     "order.view", "order.status.update",
                     // Quality control (check and view)
                     "quality.check", "quality.view",
                     // Notifications
                     "notification.view", "notification.update",
                     // Employee (own clock-in and view)
                     "employee.clock", "employee.view"
             ))));

        // IRONER - Ironing and finishing staff
        role("IRONER", "Ironing and finishing staff", Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     // Order management (view and status updates)
                     "order.view", "order.status.update",
                     // Quality control (check and view)
                     "quality.check", "quality.view",
                     // Notifications
                     "notification.view", "notification.update",
                     // Employee (own clock-in and view)
                     "employee.clock", "employee.view"
             ))));

        // DELIVERY - Delivery personnel
        role("DELIVERY", "Delivery personnel", Role.RoleType.BUSINESS, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     // Order management (view and status updates)
                     "order.view", "order.status.update",
                     // Notifications
                     "notification.view", "notification.update",
                     // Employee (own clock-in and view)
                     "employee.clock", "employee.view"
             ))));

        // ------- Platform staff roles (only created once, idempotent via roleRepository.count() guard) -------
        // PLATFORM_ADMIN - all-business management, can change plans, cannot delete businesses
        roleIfAbsent("PLATFORM_ADMIN", "Platform admin — manages all businesses and plans", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "platform.businesses.view", "platform.businesses.manage",
                     "platform.plans.manage", "platform.billing.manage", "platform.audit.view"
             ))));

        // SUPPORT_AGENT - operational view with masked PII, read-only
        roleIfAbsent("SUPPORT_AGENT", "Support agent — masked read-only operational data", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "platform.businesses.view", "platform.support.view"
             ))));

        // BILLING_ADMIN - plan and trial management only
        roleIfAbsent("BILLING_ADMIN", "Billing admin — plan and trial management", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "platform.businesses.view", "platform.billing.manage"
             ))));

        // READONLY_AUDITOR - full read-only access for compliance
        roleIfAbsent("READONLY_AUDITOR", "Read-only auditor — compliance read access", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "platform.businesses.view", "platform.audit.view"
             ))));

        log.info("Created {} roles.", roleRepository.count());
    }

    /** Creates a role only if one with that name does not already exist. */
    private void roleIfAbsent(String name, String desc, Role.RoleType type, boolean system,
                              LocalDateTime now, Set<Permission> perms) {
        if (roleRepository.findByName(name).isPresent()) return;
        role(name, desc, type, system, now, perms);
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

        // SUPER_ADMIN - Full platform access
        syncRole("SUPER_ADMIN",
            new HashSet<>(permissionRepository.findAll()));

        // BUSINESS_OWNER - Full business control (all BUSINESS and SHOP scope permissions)
        syncRole("BUSINESS_OWNER",
            new HashSet<>(permissionRepository.findByScopeIn(Arrays.asList(
                Permission.PermissionScope.BUSINESS, Permission.PermissionScope.SHOP))));

        // SHOP_MANAGER - Operational control with necessary business permissions
        syncRole("SHOP_MANAGER",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                // Order management (full control)
                "order.view", "order.create", "order.update", "order.delete",
                "order.status.update", "order.transfer",
                // Payment management (full control)
                "payment.process", "payment.refund", "payment.view",
                // Inventory management (full control)
                "inventory.view", "inventory.manage", "inventory.adjust",
                // Quality control (shop-level checks)
                "quality.check",
                // Quality management (business-level quality oversight)
                "quality.view", "quality.manage",
                // Customer management (full control)
                "customer.view", "customer.create", "customer.update",
                // Reporting (all reports including dashboard)
                "report.view.operational", "report.view.financial", "report.export",
                // Notifications (view and update)
                "notification.view", "notification.update",
                // Employee management (full control)
                "employee.view", "employee.clock", "employee.manage",
                // User management (can view users)
                "user.view"
            ))));

        // FRONT_DESK - Order intake, payments, and customer service
        syncRole("FRONT_DESK",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                // Order management (create and view)
                "order.view", "order.create", "order.update", "order.status.update",
                // Payment processing
                "payment.process", "payment.view",
                // Customer management
                "customer.view", "customer.create", "customer.update",
                // Reporting (operational dashboard)
                "report.view.operational",
                // Notifications
                "notification.view", "notification.update",
                // Employee (own clock-in and view)
                "employee.clock", "employee.view"
            ))));

        // WASHER - Laundry technicians
        syncRole("WASHER",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                // Order management (view and status updates)
                "order.view", "order.status.update",
                // Quality control (check and view)
                "quality.check", "quality.view",
                // Notifications
                "notification.view", "notification.update",
                // Employee (own clock-in and view)
                "employee.clock", "employee.view"
            ))));

        // IRONER - Ironing and finishing staff
        syncRole("IRONER",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                // Order management (view and status updates)
                "order.view", "order.status.update",
                // Quality control (check and view)
                "quality.check", "quality.view",
                // Notifications
                "notification.view", "notification.update",
                // Employee (own clock-in and view)
                "employee.clock", "employee.view"
            ))));

        // DELIVERY - Delivery personnel
        syncRole("DELIVERY",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                // Order management (view and status updates)
                "order.view", "order.status.update",
                // Notifications
                "notification.view", "notification.update",
                // Employee (own clock-in and view)
                "employee.clock", "employee.view"
            ))));

        // Platform roles
        syncRole("PLATFORM_ADMIN",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "platform.businesses.view", "platform.businesses.manage",
                "platform.plans.manage", "platform.billing.manage", "platform.audit.view"
            ))));

        syncRole("SUPPORT_AGENT",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "platform.businesses.view", "platform.support.view"
            ))));

        syncRole("BILLING_ADMIN",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "platform.businesses.view", "platform.billing.manage"
            ))));

        syncRole("READONLY_AUDITOR",
            new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                "platform.businesses.view", "platform.audit.view"
            ))));
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
        auth.setPasswordHash(passwordEncoder.encode("Admin@123"));
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

    /**
     * Seeds the three base subscription plans (FREE / STARTER / PRO) idempotently.
     * ENTERPRISE is not seeded — it is created manually per customer by SUPER_ADMIN.
     */
    private void seedSubscriptionPlans() {
        log.info("Seeding subscription plans...");
        seedPlan(SubscriptionPlan.builder()
                .tier("FREE")
                .name("Free")
                .description("Perfect for getting started — no credit card required.")
                .price(BigDecimal.ZERO)
                .maxShops(1).maxUsers(3).maxEmployees(10).maxOrdersPerMonth(500)
                .maxServiceCatalogItems(20).maxInventoryItems(50).dataRetentionDays(365)
                .advancedAnalytics(false).exportReports(false).loyaltyProgram(true)
                .qualityControl(true).apiAccess(false).prioritySupport(false)
                .customBranding(false).multiShopReporting(false).isActive(true)
                .build());

        seedPlan(SubscriptionPlan.builder()
                .tier("STARTER")
                .name("Starter")
                .description("Grow across multiple locations with core business tools.")
                .price(new BigDecimal("9900.00"))   // ₦9,900/month
                .maxShops(3).maxUsers(15).maxEmployees(30).maxOrdersPerMonth(2000)
                .maxServiceCatalogItems(100).maxInventoryItems(300).dataRetentionDays(730)
                .advancedAnalytics(false).exportReports(false).loyaltyProgram(true)
                .qualityControl(true).apiAccess(false).prioritySupport(false)
                .customBranding(false).multiShopReporting(true).isActive(true)
                .build());

        seedPlan(SubscriptionPlan.builder()
                .tier("PRO")
                .name("Pro")
                .description("Unlimited growth with advanced analytics, exports and API access.")
                .price(new BigDecimal("24900.00"))  // ₦24,900/month
                .maxShops(-1).maxUsers(-1).maxEmployees(-1).maxOrdersPerMonth(-1)
                .maxServiceCatalogItems(-1).maxInventoryItems(-1).dataRetentionDays(1825)
                .advancedAnalytics(true).exportReports(true).loyaltyProgram(true)
                .qualityControl(true).apiAccess(true).prioritySupport(true)
                .customBranding(true).multiShopReporting(true).isActive(true)
                .build());

        log.info("Subscription plans seeded. Total: {}", subscriptionPlanRepository.count());
    }

    /** Inserts a plan row only if no row with the same tier already exists. */
    private void seedPlan(SubscriptionPlan plan) {
        if (subscriptionPlanRepository.existsByTier(plan.getTier())) {
            log.debug("Subscription plan '{}' already exists — skipping.", plan.getTier());
            return;
        }
        subscriptionPlanRepository.save(plan);
        log.info("Seeded subscription plan: {}", plan.getTier());
    }
}
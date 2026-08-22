package com.jjenus.qliina_management.common.seed;

import com.jjenus.qliina_management.billing.model.BillingPlan;
import com.jjenus.qliina_management.billing.model.PlanFeature;
import com.jjenus.qliina_management.billing.model.PlanStatus;
import com.jjenus.qliina_management.billing.model.PlanVersion;
import com.jjenus.qliina_management.billing.repository.BillingPlanRepository;
import com.jjenus.qliina_management.billing.repository.PlanFeatureRepository;
import com.jjenus.qliina_management.billing.repository.PlanVersionRepository;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.identity.model.*;
import com.jjenus.qliina_management.identity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository            roleRepository;
    private final PermissionRepository      permissionRepository;
    private final UserRepository            userRepository;
    private final AuthAccountRepository     authAccountRepository;
    private final BusinessRepository        businessRepository;
    private final BillingPlanRepository     billingPlanRepository;
    private final PlanVersionRepository     planVersionRepository;
    private final PlanFeatureRepository     planFeatureRepository;
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
        ensureWorkerInventoryPermissions();
        ensurePlatformRolePermissions();
        createPlatformBusiness();
        createSuperAdmin();
        seedBillingPlans();
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
        perm("inventory.use",    "Log Stock Usage",  "Log consumables used on orders", "INVENTORY", "SHOP", true);
        perm("inventory.request","Request Supplies", "Request restock of supplies",    "INVENTORY", "SHOP", true);

        // Quality control
        perm("quality.check",  "Perform QC Checks", "Perform QC on orders",  "QUALITY", "SHOP",     true);
        perm("quality.manage", "Manage Quality",    "Manage QC checklists",  "QUALITY", "BUSINESS", false);
        perm("quality.view",   "View QC Reports",   "View quality metrics",  "QUALITY", "BUSINESS", true);

        // Notifications
        perm("notification.view",   "View Notifications",    "View notifications",              "NOTIFICATION", "BUSINESS", true);
        perm("notification.send",   "Send Notifications",    "Send notifications to users",     "NOTIFICATION", "BUSINESS", false);
        perm("notification.update", "Update Notifications",  "Mark notifications as read",      "NOTIFICATION", "BUSINESS", true);
        perm("notification.manage", "Manage Notifications",  "Manage templates and settings",   "NOTIFICATION", "BUSINESS", false);
        perm("notification.config.view",   "View Notification Config",  "View notification channel configuration", "NOTIFICATION", "BUSINESS", false);
        perm("notification.config.manage", "Manage Notification Config","Manage notification channel configuration", "NOTIFICATION", "BUSINESS", false);

        // Employee management
        perm("employee.view",   "View Employees",   "View employee details",           "EMPLOYEE", "BUSINESS", true);
        perm("employee.clock",  "Clock In/Out",     "Clock in and out of shifts",     "EMPLOYEE", "SHOP",     true);
        perm("employee.manage", "Manage Employees", "Manage schedules and attendance","EMPLOYEE", "BUSINESS", false);

        // Admin
        perm("admin.settings", "Manage Settings", "Manage business settings", "ADMIN", "BUSINESS", false);
        perm("admin.audit",    "View Audit Logs", "View system audit logs",   "ADMIN", "BUSINESS", false);

        // Platform roles (cross-tenant access for Qliina staff)
        // Scoped GLOBAL (not BUSINESS) so tenant BUSINESS_ADMIN roles never inherit them.
        perm("platform.businesses.view",   "View All Businesses",       "List and view all businesses",           "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.businesses.manage", "Manage All Businesses",     "Update status and plan of businesses",   "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.plans.manage",      "Manage Subscription Plans", "CRUD subscription plan definitions",     "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.support.view",      "Support View",              "View operational data (masked PII)",     "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.billing.manage",    "Manage Billing",            "Manage plan tiers and trial extensions", "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.audit.view",        "Platform Audit View",       "Full read-only audit access",            "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.coupons.manage",    "Manage Coupons",            "Create and manage discount coupons",     "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.stats.view",        "View Platform Stats",       "View cross-tenant platform statistics",  "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.users.manage",      "Manage Platform Users",     "Manage platform staff accounts",         "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.settings.manage",   "Manage System Settings",    "Manage platform settings and feature flags", "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.notifications.manage", "Manage Platform Notifications", "Send broadcasts and manage system templates", "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.impersonate",       "Impersonate Tenant Users",  "Login as a tenant user for support",     "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.audit.export",      "Export Audit Logs",         "Export audit logs for compliance",       "PLATFORM_ADMIN", "GLOBAL", false);

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

        // BUSINESS_ADMIN - Full business control (all BUSINESS and SHOP scope permissions)
        role("BUSINESS_ADMIN", "Business owner — full business control", Role.RoleType.BUSINESS, true, now,
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
                     "inventory.use", "inventory.request",
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
                     // Inventory (log consumables used, request supplies)
                     "inventory.use", "inventory.request",
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
                     // Inventory (log consumables used, request supplies)
                     "inventory.use", "inventory.request",
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
                     // Inventory (request supplies)
                     "inventory.request",
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

    /**
     * Idempotent top-up for databases created before the worker inventory
     * permissions existed (createRoles() only runs once per database).
     */
    private void ensureWorkerInventoryPermissions() {
        grantIfMissing("WASHER", "inventory.use");
        grantIfMissing("WASHER", "inventory.request");
        grantIfMissing("IRONER", "inventory.use");
        grantIfMissing("IRONER", "inventory.request");
        grantIfMissing("DELIVERY", "inventory.request");
        grantIfMissing("SHOP_MANAGER", "inventory.use");
        grantIfMissing("SHOP_MANAGER", "inventory.request");
    }

    /**
     * Idempotent top-up for platform roles on databases created before the
     * expanded admin permission set existed (createRoles() only runs once).
     */
    private void ensurePlatformRolePermissions() {
        // PLATFORM_ADMIN — full day-to-day platform operations
        grantIfMissing("PLATFORM_ADMIN", "platform.stats.view");
        grantIfMissing("PLATFORM_ADMIN", "platform.users.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.settings.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.notifications.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.impersonate");
        grantIfMissing("PLATFORM_ADMIN", "platform.coupons.manage");

        // SUPPORT_AGENT — operational stats visibility for support work
        grantIfMissing("SUPPORT_AGENT", "platform.stats.view");

        // BILLING_ADMIN — billing KPIs + coupon ownership (wires orphaned perm)
        grantIfMissing("BILLING_ADMIN", "platform.stats.view");
        grantIfMissing("BILLING_ADMIN", "platform.coupons.manage");

        // READONLY_AUDITOR — read-only stats + audit export for compliance
        grantIfMissing("READONLY_AUDITOR", "platform.stats.view");
        grantIfMissing("READONLY_AUDITOR", "platform.audit.export");
    }

    private void grantIfMissing(String roleName, String permissionName) {
        roleRepository.findByName(roleName).ifPresent(role -> {
            boolean has = role.getPermissions().stream()
                    .anyMatch(p -> p.getName().equals(permissionName));
            if (!has) {
                permissionRepository.findByName(permissionName).ifPresent(p -> {
                    role.getPermissions().add(p);
                    roleRepository.save(role);
                    log.info("Granted '{}' to existing role '{}'", permissionName, roleName);
                });
            }
        });
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

    // -------------------------------------------------------------------------
    // Billing schema seeding (source of truth — MD §2 plans/versions/features)
    // -------------------------------------------------------------------------

    /**
     * Seeds the base billing plans idempotently (FREE / STARTER / PRO).
     * ENTERPRISE is not seeded — it is created manually per customer via the
     * admin billing API.
     */
    private void seedBillingPlans() {
        log.info("Seeding billing plans...");
        seedBillingPlan("FREE", "Free",
                "Perfect for getting started — no credit card required.",
                BigDecimal.ZERO, features(
                        "max_shops", "1", "max_users", "3", "max_employees", "10",
                        "max_orders_per_month", "500", "max_service_catalog_items", "20",
                        "max_inventory_items", "50", "data_retention_days", "365",
                        "advancedAnalytics", "false", "exportReports", "false",
                        "loyaltyProgram", "true", "qualityControl", "true",
                        "apiAccess", "false", "prioritySupport", "false",
                        "customBranding", "false", "multiShopReporting", "false"));

        seedBillingPlan("STARTER", "Starter",
                "Grow across multiple locations with core business tools.",
                new BigDecimal("9900.00"), features(
                        "max_shops", "3", "max_users", "15", "max_employees", "30",
                        "max_orders_per_month", "2000", "max_service_catalog_items", "100",
                        "max_inventory_items", "300", "data_retention_days", "730",
                        "advancedAnalytics", "false", "exportReports", "false",
                        "loyaltyProgram", "true", "qualityControl", "true",
                        "apiAccess", "false", "prioritySupport", "false",
                        "customBranding", "false", "multiShopReporting", "true"));

        seedBillingPlan("PRO", "Pro",
                "Unlimited growth with advanced analytics, exports and API access.",
                new BigDecimal("24900.00"), features(
                        "max_shops", "-1", "max_users", "-1", "max_employees", "-1",
                        "max_orders_per_month", "-1", "max_service_catalog_items", "-1",
                        "max_inventory_items", "-1", "data_retention_days", "1825",
                        "advancedAnalytics", "true", "exportReports", "true",
                        "loyaltyProgram", "true", "qualityControl", "true",
                        "apiAccess", "true", "prioritySupport", "true",
                        "customBranding", "true", "multiShopReporting", "true"));

        log.info("Billing plans seeded. Total: {}", billingPlanRepository.count());
    }

    private void seedBillingPlan(String name, String displayName, String description, BigDecimal price,
                                 Map<String, String> features) {
        boolean exists = billingPlanRepository.findAll().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
        if (exists) {
            log.debug("Billing plan '{}' already exists — skipping.", name);
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        BillingPlan plan = BillingPlan.builder()
                .name(displayName).description(description).status(PlanStatus.ACTIVE).build();
        plan.setCreatedAt(now);
        plan.setCreatedBy(SYSTEM_USER_ID);
        plan = billingPlanRepository.save(plan);

        PlanVersion version = PlanVersion.builder()
                .plan(plan).price(price).currency("NGN").effectiveFrom(now).build();
        version.setCreatedAt(now);
        version.setCreatedBy(SYSTEM_USER_ID);
        planVersionRepository.save(version);

        for (Map.Entry<String, String> e : features.entrySet()) {
            PlanFeature feature = PlanFeature.builder()
                    .plan(plan).featureKey(e.getKey()).value(e.getValue())
                    .isHardLimit(HARD_LIMIT_KEYS.contains(e.getKey())).build();
            feature.setCreatedAt(now);
            feature.setCreatedBy(SYSTEM_USER_ID);
            planFeatureRepository.save(feature);
        }
        log.info("Seeded billing plan: {} ({} {}/month)", displayName, price, "NGN");
    }

    private static final Set<String> HARD_LIMIT_KEYS = Set.of(
            "max_shops", "max_users", "max_employees", "max_orders_per_month",
            "max_service_catalog_items", "max_inventory_items", "data_retention_days");

    private Map<String, String> features(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
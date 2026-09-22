package com.jjenus.qliina_management.common.seed;

import com.jjenus.qliina_management.identity.model.Permission;
import com.jjenus.qliina_management.identity.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seeds the permission catalog used by every role. Reference data — runs in all
 * profiles, create-only and idempotent (skips names that already exist).
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class PermissionSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;

    private static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting permission seeding...");
        createPermissions();
    }

    private void createPermissions() {
        log.info("Ensuring permissions exist...");

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
        perm("platform.payments.manage",   "Manage Payment Providers",  "Control which payment providers businesses can connect", "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.notifications.manage", "Manage Platform Notifications", "Send broadcasts and manage system templates", "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.impersonate",       "Impersonate Tenant Users",  "Login as a tenant user for support",     "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.audit.export",      "Export Audit Logs",         "Export audit logs for compliance",       "PLATFORM_ADMIN", "GLOBAL", false);

        // Complaints / support tickets
        // Business-scope perms let BUSINESS_ADMIN (all BUSINESS+SHOP perms) inherit them
        // automatically; GLOBAL-scope perms stay platform-staff-only.
        perm("complaint.view",        "View Complaints",        "View and track customer complaints and support tickets", "CUSTOMER_SERVICE", "BUSINESS", true);
        perm("complaint.create",      "Create Complaints",      "Log customer complaints and support tickets",            "CUSTOMER_SERVICE", "BUSINESS", true);
        perm("complaint.resolve",     "Resolve Complaints",     "Resolve or reject complaints",                           "CUSTOMER_SERVICE", "BUSINESS", false);
        perm("platform.complaints.view",   "View All Complaints",   "View complaints across all businesses",      "PLATFORM_ADMIN", "GLOBAL", false);
        perm("platform.complaints.manage", "Manage All Complaints", "Triage and resolve complaints platform-wide", "PLATFORM_ADMIN", "GLOBAL", false);

        log.info("Permissions check complete. Total: {}", permissionRepository.count());
    }

    private void perm(String name, String display, String desc, String cat, String scope, boolean def) {
        if (permissionRepository.findByName(name).isPresent()) return;
        Permission p = new Permission();
        p.setName(name); p.setDisplayName(display); p.setDescription(desc);
        p.setCategory(cat); p.setScope(Permission.PermissionScope.valueOf(scope)); p.setIsDefault(def);
        p.setCreatedAt(LocalDateTime.now()); p.setCreatedBy(SYSTEM_USER_ID);
        permissionRepository.save(p);
        log.debug("Created permission: {}", name);
    }
}
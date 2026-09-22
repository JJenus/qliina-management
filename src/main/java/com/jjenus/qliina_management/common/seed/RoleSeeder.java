package com.jjenus.qliina_management.common.seed;

import com.jjenus.qliina_management.identity.model.Permission;
import com.jjenus.qliina_management.identity.model.Role;
import com.jjenus.qliina_management.identity.repository.PermissionRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seeds the system roles (SUPER_ADMIN, BUSINESS_ADMIN, SHOP_MANAGER, workers,
 * platform staff) and their permission matrices. Reference data — runs in all
 * profiles, create-only and idempotent (roles are created once, never synced,
 * and never repaired by later runs). Runs after {@link PermissionSeeder}.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RoleSeeder implements CommandLineRunner {

    private final RoleRepository            roleRepository;
    private final PermissionRepository      permissionRepository;

    private static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    @Transactional
    public void run(String... args) {
        createRoles();
        ensureWorkerInventoryPermissions();
        ensurePlatformRolePermissions();
        ensureComplaintPermissions();
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
                     "user.view",
                     // Complaints (full tenant complaint handling)
                     "complaint.view", "complaint.create", "complaint.resolve"
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
                     "employee.clock", "employee.view",
                     // Complaints (log and track)
                     "complaint.view", "complaint.create"
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
                     "platform.plans.manage", "platform.billing.manage", "platform.audit.view",
                     "platform.payments.manage", "platform.support.view",
                     "platform.complaints.view", "platform.complaints.manage"
             ))));

        // SUPPORT_AGENT - operational view with masked PII, read-only
        roleIfAbsent("SUPPORT_AGENT", "Support agent — masked read-only operational data", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "platform.businesses.view", "platform.support.view",
                     "platform.complaints.view"
             ))));

        // BILLING_ADMIN - plan and trial management only
        roleIfAbsent("BILLING_ADMIN", "Billing admin — plan and trial management", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "platform.businesses.view", "platform.billing.manage"
             ))));

        // READONLY_AUDITOR - full read-only access for compliance
        roleIfAbsent("READONLY_AUDITOR", "Read-only auditor — compliance read access", Role.RoleType.PLATFORM, true, now,
             new HashSet<>(permissionRepository.findByNameIn(Arrays.asList(
                     "platform.businesses.view", "platform.audit.view",
                     "platform.complaints.view"
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
        // SUPER_ADMIN is created with "all permissions at that time"; new permissions
        // added later must be topped up here too (createRoles() only runs once).
        roleRepository.findByName("SUPER_ADMIN").ifPresent(role -> {
            Set<String> have = role.getPermissions().stream()
                    .map(Permission::getName).collect(Collectors.toSet());
            permissionRepository.findAll().forEach(p -> {
                if (have.add(p.getName())) role.getPermissions().add(p);
            });
            roleRepository.save(role);
        });

        // PLATFORM_ADMIN — full day-to-day platform operations
        grantIfMissing("PLATFORM_ADMIN", "platform.stats.view");
        grantIfMissing("PLATFORM_ADMIN", "platform.users.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.businesses.purge");
        grantIfMissing("PLATFORM_ADMIN", "platform.settings.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.payments.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.notifications.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.impersonate");
        grantIfMissing("PLATFORM_ADMIN", "platform.coupons.manage");
        grantIfMissing("PLATFORM_ADMIN", "platform.support.view");

        // SUPER_ADMIN / BUSINESS_ADMIN are created with "all (business) permissions at
        // that time"; new permissions added later must be topped up here too.

        // BUSINESS_ADMIN — keep parity with the "all BUSINESS + SHOP scope" contract.
        roleRepository.findByName("BUSINESS_ADMIN").ifPresent(role -> {
            Set<String> have = role.getPermissions().stream()
                    .map(Permission::getName).collect(Collectors.toSet());
            permissionRepository.findByScopeIn(Arrays.asList(
                            Permission.PermissionScope.BUSINESS, Permission.PermissionScope.SHOP))
                    .forEach(p -> {
                        if (have.add(p.getName())) role.getPermissions().add(p);
                    });
            roleRepository.save(role);
        });

        // SUPPORT_AGENT — operational stats visibility for support work
        grantIfMissing("SUPPORT_AGENT", "platform.stats.view");

        // BILLING_ADMIN — billing KPIs + coupon ownership (wires orphaned perm)
        grantIfMissing("BILLING_ADMIN", "platform.stats.view");
        grantIfMissing("BILLING_ADMIN", "platform.coupons.manage");

        // READONLY_AUDITOR — read-only stats + audit export for compliance
        grantIfMissing("READONLY_AUDITOR", "platform.stats.view");
        grantIfMissing("READONLY_AUDITOR", "platform.audit.export");
    }

    /**
     * Idempotent top-up for complaint permissions on databases created before
     * the complaints domain existed (createRoles() only runs once per database).
     */
    private void ensureComplaintPermissions() {
        // FRONT_DESK — log and track complaints
        grantIfMissing("FRONT_DESK", "complaint.view");
        grantIfMissing("FRONT_DESK", "complaint.create");
        // SHOP_MANAGER — full tenant complaint handling
        grantIfMissing("SHOP_MANAGER", "complaint.view");
        grantIfMissing("SHOP_MANAGER", "complaint.create");
        grantIfMissing("SHOP_MANAGER", "complaint.resolve");
        // Platform staff — cross-tenant inbox
        grantIfMissing("PLATFORM_ADMIN", "platform.complaints.view");
        grantIfMissing("PLATFORM_ADMIN", "platform.complaints.manage");
        // SUPPORT_AGENT / READONLY_AUDITOR — view-only complaints
        grantIfMissing("SUPPORT_AGENT", "platform.complaints.view");
        grantIfMissing("READONLY_AUDITOR", "platform.complaints.view");
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
}
package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.common.seed.PermissionSeeder;
import com.jjenus.qliina_management.common.seed.RoleSeeder;
import com.jjenus.qliina_management.identity.model.Permission;
import com.jjenus.qliina_management.identity.model.Role;
import com.jjenus.qliina_management.identity.repository.PermissionRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the identity seed contracts:
 *
 * <ul>
 *   <li>All system roles are seeded exactly once with their full permission matrix
 *       (this is where SHOP_MANAGER is asserted to include {@code expenses.manage}).</li>
 *   <li>Seeding is create-only: re-running the initializer is a no-op and never
 *       rewrites (syncs) permissions on existing roles.</li>
 * </ul>
 *
 * These tests would have failed before the sync logic was removed: the one-time
 * seed granted SHOP_MANAGER {@code expenses.manage}, but a per-startup sync then
 * stripped it from the live role-permission links.
 */
class RoleSeedIntegrationTest extends BaseIntegrationTest {

    /** Total number of permissions defined by the seed (source of truth). */
    private static final int TOTAL_PERMISSIONS = 53;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PermissionSeeder permissionSeeder;

    @Autowired
    private RoleSeeder roleSeeder;

    @Test
    void seed_createsEverySystemRole() {
        for (String role : Arrays.asList(
                "SUPER_ADMIN", "BUSINESS_ADMIN", "SHOP_MANAGER", "FRONT_DESK",
                "WASHER", "IRONER", "DELIVERY",
                "PLATFORM_ADMIN", "SUPPORT_AGENT", "BILLING_ADMIN", "READONLY_AUDITOR")) {
            assertThat(roleRepository.findByName(role))
                    .as("system role %s exists", role)
                    .isPresent();
        }
    }

    @Test
    void seed_definesExpectedPermissionCatalog() {
        assertThat(permissionRepository.count()).isEqualTo(TOTAL_PERMISSIONS);
        // Sanity: a few permission entries that role assertions depend on.
        assertThat(permissionRepository.findByName("expenses.manage")).isPresent();
        assertThat(permissionRepository.findByName("order.view")).isPresent();
    }

    @Test
    void seed_assignsExactPermissionMatrixToEverySystemRole() {
        Set<String> all = permissionNames(permissionRepository.findAll());
        Set<String> global = permissionNames(
                permissionRepository.findByScopeIn(Arrays.asList(Permission.PermissionScope.GLOBAL)));

        Set<String> washerIroner = set(
                "order.view", "order.status.update",
                "quality.check", "quality.view",
                "notification.view", "notification.update",
                "employee.clock", "employee.view",
                // Worker stock usage + supply requests
                "inventory.use", "inventory.request");

        Set<String> delivery = set(
                "order.view", "order.status.update",
                "notification.view", "notification.update",
                "employee.clock", "employee.view",
                // Supply requests only (no hands-on stock usage)
                "inventory.request");

        Map<String, Set<String>> expected = Map.ofEntries(
                Map.entry("SUPER_ADMIN", all),
                Map.entry("BUSINESS_ADMIN", subtract(all, global)),
                Map.entry("SHOP_MANAGER", set(
                        // Order management (full control)
                        "order.view", "order.create", "order.update", "order.delete",
                        "order.status.update", "order.transfer",
                        // Payment management (full control)
                        "payment.process", "payment.refund", "payment.view",
                        // Inventory management (full control)
                        "inventory.view", "inventory.manage", "inventory.adjust",
                        // Manager also uses stock and receives supply requests
                        "inventory.use", "inventory.request",
                        // Quality control + quality management
                        "quality.check", "quality.view", "quality.manage",
                        // Customer management (full control)
                        "customer.view", "customer.create", "customer.update",
                        // Reporting + wages expense management
                        "report.view.operational", "report.view.financial", "report.export",
                        "expenses.manage",
                        // Notifications
                        "notification.view", "notification.update",
                        // Employee management (full control)
                        "employee.view", "employee.clock", "employee.manage",
                        // Users
                        "user.view")),
                Map.entry("FRONT_DESK", set(
                        "order.view", "order.create", "order.update", "order.status.update",
                        "payment.process", "payment.view",
                        "customer.view", "customer.create", "customer.update",
                        "report.view.operational",
                        "notification.view", "notification.update",
                        "employee.clock", "employee.view")),
                Map.entry("WASHER", washerIroner),
                Map.entry("IRONER", washerIroner),
                Map.entry("DELIVERY", delivery),
                Map.entry("PLATFORM_ADMIN", set(
                        "platform.businesses.view", "platform.businesses.manage",
                        "platform.plans.manage", "platform.billing.manage",
                        "platform.audit.view",
                        // ensurePlatformRolePermissions top-up
                        "platform.stats.view", "platform.users.manage",
                        "platform.settings.manage", "platform.notifications.manage",
                        "platform.impersonate", "platform.coupons.manage")),
                Map.entry("SUPPORT_AGENT", set(
                        "platform.businesses.view", "platform.support.view",
                        "platform.stats.view")),
                Map.entry("BILLING_ADMIN", set(
                        "platform.businesses.view", "platform.billing.manage",
                        "platform.stats.view", "platform.coupons.manage")),
                Map.entry("READONLY_AUDITOR", set(
                        "platform.businesses.view", "platform.audit.view",
                        "platform.stats.view", "platform.audit.export")));

        for (Map.Entry<String, Set<String>> e : expected.entrySet()) {
            assertThat(permissionNames(roleRepository.findByName(e.getKey())
                    .orElseThrow().getPermissions()))
                    .as("permission matrix for %s", e.getKey())
                    .containsExactlyInAnyOrderElementsOf(e.getValue());
        }
    }

    @Test
    @Transactional
    void seed_isIdempotentAndNeverRewritesExistingRoleLinks() {
        // Baseline snapshot of every role's permission links.
        Map<String, Set<String>> before = snapshot();

        permissionSeeder.run();
        roleSeeder.run();

        Map<String, Set<String>> after = snapshot();
        for (String role : before.keySet()) {
            assertThat(after.get(role))
                    .as("re-running the initializer must not change %s permissions", role)
                    .containsExactlyInAnyOrderElementsOf(before.get(role));
        }
        assertThat(roleRepository.count()).isEqualTo(11);
        assertThat(permissionRepository.count()).isEqualTo(TOTAL_PERMISSIONS);

        // Prove there is no per-startup sync that repairs drift: drop the
        // SHOP_MANAGER expenses link, re-run the initializer, and assert the
        // seed does NOT re-add it (roles are created once and never touched).
        Role shopManager = roleRepository.findByName("SHOP_MANAGER").orElseThrow();
        shopManager.setPermissions(shopManager.getPermissions().stream()
                .filter(p -> !p.getName().equals("expenses.manage"))
                .collect(Collectors.toSet()));
        roleRepository.save(shopManager);

        permissionSeeder.run();
        roleSeeder.run();

        Set<String> names = permissionNames(
                roleRepository.findByName("SHOP_MANAGER").orElseThrow().getPermissions());
        assertThat(names)
                .as("seed must not repair drift on an already-seeded role")
                .doesNotContain("expenses.manage");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Map<String, Set<String>> snapshot() {
        return roleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        Role::getName,
                        r -> permissionNames(r.getPermissions())));
    }

    private static Set<String> permissionNames(Collection<Permission> permissions) {
        return permissions.stream().map(Permission::getName).collect(Collectors.toSet());
    }

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static Set<String> subtract(Set<String> from, Set<String> remove) {
        Set<String> result = new HashSet<>(from);
        result.removeAll(remove);
        return result;
    }
}
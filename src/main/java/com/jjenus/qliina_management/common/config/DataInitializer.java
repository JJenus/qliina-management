package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.identity.model.*;
import com.jjenus.qliina_management.identity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final PasswordEncoder passwordEncoder;
    
    // System user ID for audit fields during initialization
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    
    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing database with default data...");
        
        // Create default permissions
        createPermissions();
        
        // Create default roles
        createRoles();
        
        // Create super admin user
        createSuperAdmin();
        
        log.info("Database initialization completed");
    }
    
    private void createPermissions() {
        if (permissionRepository.count() > 0) {
            log.info("Permissions already exist, skipping creation");
            return;
        }
        
        log.info("Creating default permissions...");
        
        // User permissions
        createPermission("user.view", "View Users", "View user details", "USER_MANAGEMENT", "BUSINESS", true);
        createPermission("user.create", "Create Users", "Create new users", "USER_MANAGEMENT", "BUSINESS", true);
        createPermission("user.update", "Update Users", "Update existing users", "USER_MANAGEMENT", "BUSINESS", true);
        createPermission("user.delete", "Delete Users", "Delete or deactivate users", "USER_MANAGEMENT", "BUSINESS", false);
        
        // Order permissions
        createPermission("order.view", "View Orders", "View order details", "ORDER_MANAGEMENT", "SHOP", true);
        createPermission("order.create", "Create Orders", "Create new orders", "ORDER_MANAGEMENT", "SHOP", true);
        createPermission("order.update", "Update Orders", "Update existing orders", "ORDER_MANAGEMENT", "SHOP", true);
        createPermission("order.delete", "Delete Orders", "Delete or cancel orders", "ORDER_MANAGEMENT", "SHOP", true);
        createPermission("order.status.update", "Update Order Status", "Change order status", "ORDER_MANAGEMENT", "SHOP", true);
        createPermission("order.transfer", "Transfer Orders", "Transfer orders between shops", "ORDER_MANAGEMENT", "SHOP", true);
        
        // Payment permissions
        createPermission("payment.process", "Process Payments", "Process payments for orders", "PAYMENT", "SHOP", true);
        createPermission("payment.refund", "Process Refunds", "Process refunds for orders", "PAYMENT", "SHOP", false);
        createPermission("payment.view", "View Payments", "View payment details", "PAYMENT", "SHOP", true);
        
        // Customer permissions
        createPermission("customer.view", "View Customers", "View customer details", "CUSTOMER_MANAGEMENT", "BUSINESS", true);
        createPermission("customer.create", "Create Customers", "Create new customers", "CUSTOMER_MANAGEMENT", "BUSINESS", true);
        createPermission("customer.update", "Update Customers", "Update existing customers", "CUSTOMER_MANAGEMENT", "BUSINESS", true);
        createPermission("customer.delete", "Delete Customers", "Delete or deactivate customers", "CUSTOMER_MANAGEMENT", "BUSINESS", false);
        
        // Report permissions
        createPermission("report.view.financial", "View Financial Reports", "View financial reports", "REPORTING", "BUSINESS", false);
        createPermission("report.view.operational", "View Operational Reports", "View operational reports", "REPORTING", "BUSINESS", true);
        createPermission("report.export", "Export Reports", "Export reports to files", "REPORTING", "BUSINESS", false);
        
        // Inventory permissions
        createPermission("inventory.view", "View Inventory", "View inventory items", "INVENTORY", "SHOP", true);
        createPermission("inventory.manage", "Manage Inventory", "Create, update, delete inventory", "INVENTORY", "SHOP", false);
        createPermission("inventory.adjust", "Adjust Stock", "Adjust stock levels", "INVENTORY", "SHOP", true);
        
        // Quality Control permissions
        createPermission("quality.check", "Perform Quality Checks", "Perform quality checks on orders", "QUALITY", "SHOP", true);
        createPermission("quality.manage", "Manage Quality", "Manage quality checklists and settings", "QUALITY", "BUSINESS", false);
        createPermission("quality.view", "View Quality Reports", "View quality metrics and reports", "QUALITY", "BUSINESS", true);
        
        // Admin permissions
        createPermission("admin.settings", "Manage Settings", "Manage business settings", "ADMIN", "BUSINESS", false);
        createPermission("admin.audit", "View Audit Logs", "View system audit logs", "ADMIN", "BUSINESS", false);
        
        log.info("Created {} permissions", permissionRepository.count());
    }
    
    private void createPermission(String name, String displayName, String description, 
                                  String category, String scope, boolean isDefault) {
        Permission permission = new Permission();
        permission.setName(name);
        permission.setDisplayName(displayName);
        permission.setDescription(description);
        permission.setCategory(category);
        permission.setScope(Permission.PermissionScope.valueOf(scope));
        permission.setIsDefault(isDefault);
        
        // Manually set audit fields since DataInitializer runs outside of security context
        LocalDateTime now = LocalDateTime.now();
        permission.setCreatedAt(now);
        permission.setCreatedBy(SYSTEM_USER_ID);
        
        permissionRepository.save(permission);
    }
    
    private void createRoles() {
        if (roleRepository.count() > 0) {
            log.info("Roles already exist, skipping creation");
            return;
        }
        
        log.info("Creating default roles...");
        
        LocalDateTime now = LocalDateTime.now();
        
        // Super Admin role (platform-level)
        Role superAdmin = new Role();
        superAdmin.setName("SUPER_ADMIN");
        superAdmin.setDescription("Platform super administrator - full access");
        superAdmin.setType(Role.RoleType.PLATFORM);
        superAdmin.setIsSystem(true);
        superAdmin.setPermissions(new HashSet<>(permissionRepository.findAll()));
        superAdmin.setCreatedAt(now);
        superAdmin.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(superAdmin);
        
        // Business Owner role
        Role businessOwner = new Role();
        businessOwner.setName("BUSINESS_OWNER");
        businessOwner.setDescription("Business owner - full business control");
        businessOwner.setType(Role.RoleType.BUSINESS);
        businessOwner.setIsSystem(true);
        businessOwner.setPermissions(new HashSet<>(permissionRepository.findByScopeIn(
            Arrays.asList(Permission.PermissionScope.BUSINESS, Permission.PermissionScope.SHOP))));
        businessOwner.setCreatedAt(now);
        businessOwner.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(businessOwner);
        
        // Shop Manager role
        Role shopManager = new Role();
        shopManager.setName("SHOP_MANAGER");
        shopManager.setDescription("Shop manager - operational control of assigned shops");
        shopManager.setType(Role.RoleType.BUSINESS);
        shopManager.setIsSystem(true);
        shopManager.setPermissions(new HashSet<>(permissionRepository.findByScope(Permission.PermissionScope.SHOP)));
        shopManager.setCreatedAt(now);
        shopManager.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(shopManager);
        
        // Front Desk Associate role
        Role frontDesk = new Role();
        frontDesk.setName("FRONT_DESK");
        frontDesk.setDescription("Front desk associate - order intake and payments");
        frontDesk.setType(Role.RoleType.BUSINESS);
        frontDesk.setIsSystem(true);
        frontDesk.setPermissions(new HashSet<>(permissionRepository.findByNameIn(
            Arrays.asList("order.view", "order.create", "order.update", "payment.process", 
                         "customer.view", "customer.create", "customer.update"))));
        frontDesk.setCreatedAt(now);
        frontDesk.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(frontDesk);
        
        // Washer role
        Role washer = new Role();
        washer.setName("WASHER");
        washer.setDescription("Laundry technician - washing and drying");
        washer.setType(Role.RoleType.BUSINESS);
        washer.setIsSystem(true);
        washer.setPermissions(new HashSet<>(permissionRepository.findByNameIn(
            Arrays.asList("order.view", "order.status.update", "quality.view"))));
        washer.setCreatedAt(now);
        washer.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(washer);
        
        // Ironer role
        Role ironer = new Role();
        ironer.setName("IRONER");
        ironer.setDescription("Ironing and finishing staff");
        ironer.setType(Role.RoleType.BUSINESS);
        ironer.setIsSystem(true);
        ironer.setPermissions(new HashSet<>(permissionRepository.findByNameIn(
            Arrays.asList("order.view", "order.status.update", "quality.view"))));
        ironer.setCreatedAt(now);
        ironer.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(ironer);
        
        // Delivery Personnel role
        Role delivery = new Role();
        delivery.setName("DELIVERY");
        delivery.setDescription("Delivery personnel");
        delivery.setType(Role.RoleType.BUSINESS);
        delivery.setIsSystem(true);
        delivery.setPermissions(new HashSet<>(permissionRepository.findByNameIn(
            Arrays.asList("order.view", "order.status.update"))));
        delivery.setCreatedAt(now);
        delivery.setCreatedBy(SYSTEM_USER_ID);
        roleRepository.save(delivery);
        
        log.info("Created {} roles", roleRepository.count());
    }
    
    private void createSuperAdmin() {
        if (userRepository.findByUsername("admin").isPresent()) {
            log.info("Super admin already exists, skipping creation");
            return;
        }
        
        log.info("Creating super admin user...");
        
        LocalDateTime now = LocalDateTime.now();
        
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@laundry.com");
        admin.setPhone("+1234567890");
        admin.setFirstName("Super");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.setBusinessId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        admin.setCreatedAt(now);
        admin.setCreatedBy(SYSTEM_USER_ID);
        
        userRepository.save(admin);
        
        AuthAccount authAccount = new AuthAccount();
        authAccount.setUser(admin);
        authAccount.setPasswordHash(passwordEncoder.encode("Admin@123"));
        authAccount.setPasswordLastChanged(now);
        authAccount.setFailedAttempts(0);
        authAccount.setTotpEnabled(false);
        authAccount.setCreatedAt(now);
        authAccount.setCreatedBy(SYSTEM_USER_ID);
        
        authAccountRepository.save(authAccount);
        
        // Assign SUPER_ADMIN role
        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
            .orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not found"));
        
        UserRole userRole = new UserRole();
        userRole.setUser(admin);
        userRole.setRole(superAdminRole);
        userRole.setBusinessId(admin.getBusinessId());
        userRole.setCreatedAt(now);
        userRole.setCreatedBy(SYSTEM_USER_ID);
        
        admin.getRoles().add(userRole);
        userRepository.save(admin);
        
        log.info("Super admin created successfully");
    }
}
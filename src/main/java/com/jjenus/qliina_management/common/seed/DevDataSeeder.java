package com.jjenus.qliina_management.common.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jjenus.qliina_management.business.dto.BusinessRegistrationResponse;
import com.jjenus.qliina_management.business.dto.CreateBusinessRequest;
import com.jjenus.qliina_management.business.model.Business;
import com.jjenus.qliina_management.business.model.GarmentType;
import com.jjenus.qliina_management.business.model.ServiceType;
import com.jjenus.qliina_management.business.model.Shop;
import com.jjenus.qliina_management.business.repository.BusinessRepository;
import com.jjenus.qliina_management.business.repository.GarmentTypeRepository;
import com.jjenus.qliina_management.business.repository.ServiceTypeRepository;
import com.jjenus.qliina_management.business.repository.ShopRepository;
import com.jjenus.qliina_management.business.service.BusinessService;
import com.jjenus.qliina_management.common.util.IdGenerator;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import com.jjenus.qliina_management.employee.model.EmployeeShift;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.expense.model.Expense;
import com.jjenus.qliina_management.expense.model.ExpenseCategory;
import com.jjenus.qliina_management.expense.repository.ExpenseRepository;
import com.jjenus.qliina_management.identity.model.AuthAccount;
import com.jjenus.qliina_management.identity.model.OperatingHour;
import com.jjenus.qliina_management.identity.model.Role;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.model.UserRole;
import com.jjenus.qliina_management.identity.repository.AuthAccountRepository;
import com.jjenus.qliina_management.identity.repository.RoleRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.identity.service.BusinessConfigService;
import com.jjenus.qliina_management.inventory.model.InventoryItem;
import com.jjenus.qliina_management.inventory.model.ShopStock;
import com.jjenus.qliina_management.inventory.repository.InventoryItemRepository;
import com.jjenus.qliina_management.inventory.repository.ShopStockRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.model.OrderItem;
import com.jjenus.qliina_management.order.model.OrderItemUnit;
import com.jjenus.qliina_management.order.model.OrderTimeline;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.payment.model.OrderPayment;
import com.jjenus.qliina_management.payment.model.PaymentMethod;
import com.jjenus.qliina_management.payment.repository.OrderPaymentRepository;
import com.jjenus.qliina_management.payment.repository.PaymentMethodRepository;
import com.jjenus.qliina_management.quality.model.QualityCheck;
import com.jjenus.qliina_management.quality.repository.QualityCheckRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds a realistic demo tenant ("Qliina Demo Laundry") on the H2-backed
 * profiles (test). Runs after {@link PermissionSeeder}/{@link RoleSeeder} so
 * all roles and permissions already exist.
 *
 * Idempotent: if the demo business (slug {@code qliina-demo}) already exists,
 * the whole seed is skipped. Disable with {@code app.seed-demo.enabled=false}
 * (the integration-test base class sets exactly that).
 *
 * Seeded logins (password: {@value DEMO_PASSWORD}):
 * <ul>
 *   <li>owner — BUSINESS_ADMIN</li>
 *   <li>manager — SHOP_MANAGER</li>
 *   <li>frontdesk — FRONT_DESK</li>
 *   <li>washer1 / washer2 — WASHER</li>
 *   <li>ironer1 — IRONER</li>
 *   <li>delivery1 — DELIVERY</li>
 * </ul>
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(10)
@Profile({"dev", "test", "seed"})
@ConditionalOnProperty(prefix = "app.seed-demo", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    public static final String DEMO_SLUG = "qliina-demo";
    public static final String DEMO_PASSWORD = "Passw0rd!";
    public static final String DEMO_EMAIL_DOMAIN = "qliina-demo.com";

    private final BusinessService businessService;
    private final BusinessRepository businessRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServiceTypeRepository serviceTypeRepository;
    private final GarmentTypeRepository garmentTypeRepository;
    private final CustomerRepository customerRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ShopStockRepository shopStockRepository;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final ExpenseRepository expenseRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final BusinessConfigService businessConfigService;

    private UUID businessId;
    private ZoneId seedZone = ZoneId.systemDefault();
    private List<Shop> shops = new ArrayList<>();
    private List<ServiceType> services = new ArrayList<>();
    private List<GarmentType> garments = new ArrayList<>();
    private List<String> paymentMethods = new ArrayList<>();
    private User manager;
    private User frontDesk;
    private User washer1;
    private User ironer1;

    @Override
    @Transactional
    public void run(String... args) {
        if (businessRepository.existsBySlug(DEMO_SLUG)) {
            log.info("DevDataSeeder: demo business '{}' already exists — skipping.", DEMO_SLUG);
            return;
        }
        log.info("DevDataSeeder: creating demo tenant '{}'...", DEMO_SLUG);
        long start = System.currentTimeMillis();

        seedBusinessAndOwner();
        try {
            String tz = businessConfigService.getConfig(businessId).getTimezone();
            if (tz != null && !tz.isBlank()) seedZone = ZoneId.of(tz);
        } catch (Exception e) {
            log.warn("Failed to resolve demo business timezone, using JVM default: {}", e.getMessage());
        }
        seedShops();
        seedStaff();
        seedCustomers();
        seedInventory();
        seedOrders();
        seedExpenses();
        seedShifts();

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("DevDataSeeder: done in {}s. businessId={}, shops={}, customers={}, orders={}, users={}",
                elapsed, businessId, shops.size(),
                customerRepository.count(), orderRepository.count(), userRepository.count());
        logCredentials();
    }

    // ---------------------------------------------------------------------
    // Business + owner
    // ---------------------------------------------------------------------

    private void seedBusinessAndOwner() {
        CreateBusinessRequest req = new CreateBusinessRequest();
        req.setBusinessName("Qliina Demo Laundry");
        req.setSlug(DEMO_SLUG);
        req.setBusinessEmail("demo@" + DEMO_EMAIL_DOMAIN);
        req.setBusinessPhone("+15551234567");
        req.setShopName("Main Branch");
        req.setShopCode("MAIN");
        req.setFirstName("Demo");
        req.setLastName("Owner");
        req.setUsername("owner");
        req.setEmail("owner@" + DEMO_EMAIL_DOMAIN);
        req.setPhone("+15551234568");
        req.setPassword(DEMO_PASSWORD);
        req.setConfirmPassword(DEMO_PASSWORD);

        BusinessRegistrationResponse reg = businessService.registerBusiness(req);
        businessId = reg.getBusinessId();

        // registerBusiness always appends a random suffix to the requested slug
        // ("qliina-demo-6ca2"), which would break both the idempotency guard above
        // (existsBySlug(DEMO_SLUG) never matches → a duplicate demo tenant would
        // be seeded on every startup) and any lookup by the canonical slug.
        // Force the canonical slug here so the seed is truly create-once.
        Business business = businessRepository.findById(businessId).orElseThrow();
        business.setSlug(DEMO_SLUG);
        business.setPlan(Business.Plan.PRO);
        business.setStatus(Business.Status.ACTIVE);
        business.setTrialEndsAt(null);
        businessRepository.save(business);

        shops.add(shopRepository.findById(reg.getShopId()).orElseThrow());
        log.info("Demo business registered: businessId={}, ownerId={}", businessId, reg.getUser().getId());
    }

    private void seedShops() {
        shops.add(createShop("East Branch", "EAST", "Lagos Island, Lagos"));
        shops.add(createShop("Airport Branch", "AIRPORT", "Ikeja, Lagos"));
    }

    private Shop createShop(String name, String code, String addressLine) {
        Shop shop = new Shop();
        shop.setBusinessId(businessId);
        shop.setName(name);
        shop.setCode(code);
        shop.setPhone("+1555" + (100_0000 + shops.size()));
        shop.setEmail(name.toLowerCase().replace(" ", ".") + "@" + DEMO_EMAIL_DOMAIN);
        shop.setTimezone("Africa/Lagos");
        shop.setActive(true);
        shop.setOperatingHours(defaultHours());
        shop.setCreatedBy(businessId);
        return shopRepository.save(shop);
    }

    private List<OperatingHour> defaultHours() {
        List<OperatingHour> hours = new ArrayList<>();
        for (OperatingHour.DayOfWeek day : OperatingHour.DayOfWeek.values()) {
            boolean closed = day == OperatingHour.DayOfWeek.SUNDAY;
            hours.add(new OperatingHour(day, "08:00", closed ? "18:00" : "20:00", closed));
        }
        return hours;
    }

    // ---------------------------------------------------------------------
    // Staff (one user per seeded role)
    // ---------------------------------------------------------------------

    private void seedStaff() {
        Shop main = shops.get(0);

        manager = createUser("manager", "Sarah", "Manager", "SHOP_MANAGER", main);
        frontDesk = createUser("frontdesk", "Tunde", "Ade", "FRONT_DESK", main);
        washer1 = createUser("washer1", "Musa", "Bello", "WASHER", main);
        createUser("washer2", "Chidi", "Okafor", "WASHER", main);
        ironer1 = createUser("ironer1", "Funke", "Lawal", "IRONER", main);
        createUser("delivery1", "Emeka", "Nwosu", "DELIVERY", shops.get(1));
    }

    private User createUser(String username, String firstName, String lastName, String roleName, Shop shop) {
        if (userRepository.existsByUsername(username)) {
            return userRepository.findByUsername(username).orElseThrow();
        }
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setBusinessId(businessId);
        user.setUsername(username);
        user.setEmail(username + "@" + DEMO_EMAIL_DOMAIN);
        user.setPhone("+1555" + (200_0000 + userRepository.count()));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setPrimaryShopId(shop.getId());
        user.addShop(shop);
        user = userRepository.save(user);

        AuthAccount auth = new AuthAccount();
        auth.setUser(user);
        auth.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        auth.setPasswordLastChanged(now);
        auth.setFailedAttempts(0);
        auth.setTotpEnabled(false);
        authAccountRepository.save(auth);

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);
        ur.setBusinessId(businessId);
        ur.setShopId(shop.getId());
        user.getRoles().add(ur);
        userRepository.save(user);

        log.info("Seeded user @{} ({})", username, roleName);
        return user;
    }

    // ---------------------------------------------------------------------
    // Customers
    // ---------------------------------------------------------------------

    private void seedCustomers() {
        String[][] data = {
                {"Adaora", "Nwachukwu", "+2348011110001"},
                {"Olu", "Balogun", "+2348022220002"},
                {"Ngozi", "Eze", "+2348033330003"},
                {"Kofi", "Mensah", "+2348044440004"},
                {"Amara", "Okeke", "+2348055550005"},
                {"James", "Okafor", "+2348066660006"},
                {"Blessing", "Adeyemi", "+2348077770007"},
                {"Ibrahim", "Sule", "+2348088880008"},
                {"Chiamaka", "Umeh", "+2348099990009"},
                {"Tobi", "Afolabi", "+2348100000010"},
                {"Zainab", "Bello", "+2348111110011"},
                {"Daniel", "Obi", "+2348122220012"},
                {"Ifeoma", "Nnamdi", "+2348133330013"},
                {"Samuel", "Ogunleye", "+2348144440014"},
                {"Esther", "Akintola", "+2348155550015"},
        };
        for (String[] row : data) {
            Customer c = new Customer();
            c.setBusinessId(businessId);
            c.setFirstName(row[0]);
            c.setLastName(row[1]);
            c.setPhone(row[2]);
            c.setEmail(row[0].toLowerCase() + "." + row[1].toLowerCase() + "@gmail.com");
            c.setTotalOrders(0);
            c.setTotalSpent(BigDecimal.ZERO);
            c.setAverageOrderValue(BigDecimal.ZERO);
            c.setLoyaltyPoints(0);
            c.setLoyaltyTier("BRONZE");
            c.setEnabled(true);
            customerRepository.save(c);
        }
        log.info("Seeded {} customers.", customerRepository.count());
    }

    // ---------------------------------------------------------------------
    // Inventory
    // ---------------------------------------------------------------------

    private void seedInventory() {
        services = serviceTypeRepository.findByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(businessId);
        garments = garmentTypeRepository.findByBusinessIdAndIsActiveTrueOrderBySortOrderAsc(businessId);
        paymentMethods = paymentMethodRepository.findByBusinessIdAndIsActiveTrue(businessId).stream()
                .map(PaymentMethod::getType).distinct().toList();

        Object[][] items = {
                {"DET-WASH-001", "Concentrated Wash Detergent", InventoryItem.ItemCategory.DETERGENT, InventoryItem.UnitOfMeasure.LITER, 40, 20, new BigDecimal("4500.00"), new BigDecimal("20")},
                {"DET-POWDER-002", "Powder Laundry Detergent", InventoryItem.ItemCategory.DETERGENT, InventoryItem.UnitOfMeasure.KILOGRAM, 25, 10, new BigDecimal("3500.00"), new BigDecimal("15")},
                {"SOFT-001", "Fabric Softener", InventoryItem.ItemCategory.SOFTENER, InventoryItem.UnitOfMeasure.LITER, 30, 15, new BigDecimal("3800.00"), new BigDecimal("12")},
                {"BLEACH-001", "Chlorine Bleach", InventoryItem.ItemCategory.BLEACH, InventoryItem.UnitOfMeasure.BOTTLE, 20, 8, new BigDecimal("2200.00"), new BigDecimal("10")},
                {"STAIN-001", "Stain Remover Spray", InventoryItem.ItemCategory.STAIN_REMOVAL, InventoryItem.UnitOfMeasure.BOTTLE, 15, 6, new BigDecimal("2900.00"), new BigDecimal("8")},
                {"PKG-BAG-001", "Poly Bags (pack of 100)", InventoryItem.ItemCategory.PACKAGING, InventoryItem.UnitOfMeasure.BAG, 50, 20, new BigDecimal("1800.00"), new BigDecimal("30")},
                {"PKG-BOX-001", "Garment Boxes", InventoryItem.ItemCategory.PACKAGING, InventoryItem.UnitOfMeasure.BOX, 35, 10, new BigDecimal("4500.00"), new BigDecimal("18")},
                {"HANGER-001", "Plastic Hangers (pack of 50)", InventoryItem.ItemCategory.HANGER, InventoryItem.UnitOfMeasure.PIECE, 60, 25, new BigDecimal("2500.00"), new BigDecimal("40")},
                {"TAG-001", "Order Tags (roll of 500)", InventoryItem.ItemCategory.TAG, InventoryItem.UnitOfMeasure.ROLL, 25, 5, new BigDecimal("1600.00"), new BigDecimal("12")},
                {"LABEL-001", "Care Labels", InventoryItem.ItemCategory.LABEL, InventoryItem.UnitOfMeasure.ROLL, 30, 8, new BigDecimal("1200.00"), new BigDecimal("15")},
                {"GLOVE-001", "Nitrile Gloves (box)", InventoryItem.ItemCategory.GLOVE, InventoryItem.UnitOfMeasure.BOX, 18, 6, new BigDecimal("3000.00"), new BigDecimal("9")},
                {"MASK-001", "Face Masks (box)", InventoryItem.ItemCategory.MASK, InventoryItem.UnitOfMeasure.BOX, 22, 10, new BigDecimal("1400.00"), new BigDecimal("11")},
        };

        for (Object[] row : items) {
            InventoryItem item = new InventoryItem();
            item.setBusinessId(businessId);
            item.setSku((String) row[0]);
            item.setName((String) row[1]);
            item.setCategory((InventoryItem.ItemCategory) row[2]);
            item.setUnit((InventoryItem.UnitOfMeasure) row[3]);
            item.setReorderLevel((Integer) row[4]);
            item.setReorderQuantity((Integer) row[5]);
            item.setCurrentStock(BigDecimal.ZERO);
            item.setUnitPrice((BigDecimal) row[6]);
            item.setIsActive(true);
            item.setMinStockLevel(((BigDecimal) row[7]));
            item.setMaxStockLevel(((BigDecimal) row[7]).multiply(new BigDecimal("4")));
            item.setLocation("Store Room");
            item = inventoryItemRepository.save(item);

            for (Shop shop : shops) {
                ShopStock stock = new ShopStock();
                stock.setBusinessId(businessId);
                stock.setShopId(shop.getId());
                stock.setItem(item);
                BigDecimal qty = new BigDecimal("10").add(new BigDecimal(Math.abs(item.getName().hashCode()) % 40));
                stock.setQuantity(qty);
                stock.setLastRestocked(LocalDateTime.now().minusDays(3));
                stock.setMinimumQuantity((BigDecimal) row[7]);
                stock.setMaximumQuantity(((BigDecimal) row[7]).multiply(new BigDecimal("4")));
                stock.setReorderPoint((BigDecimal) row[7]);
                stock.setStatus(ShopStock.StockStatus.NORMAL);
                stock.setLocationDetails(shop.getName());
                shopStockRepository.save(stock);
                item.getShopStocks().add(stock);
            }
        }
        log.info("Seeded {} inventory items.", inventoryItemRepository.count());
    }

    // ---------------------------------------------------------------------
    // Orders + payments + quality checks
    // ---------------------------------------------------------------------

    private void seedOrders() {
        List<Customer> customers = customerRepository.findByBusinessId(businessId);
        Order.OrderStatus[] progression = {
                Order.OrderStatus.RECEIVED, Order.OrderStatus.WASHING, Order.OrderStatus.WASHED,
                Order.OrderStatus.IRONING, Order.OrderStatus.IRONED, Order.OrderStatus.QUALITY_CHECK,
                Order.OrderStatus.READY_FOR_PICKUP, Order.OrderStatus.COMPLETED,
        };

        for (int i = 0; i < 15; i++) {
            Customer customer = customers.get(i % customers.size());
            Shop shop = shops.get(i % shops.size());
            Order.OrderStatus status = progression[i % progression.length];
            seedOrder(customer, shop, status, i);
        }
        log.info("Seeded {} orders.", orderRepository.count());
    }

    private void seedOrder(Customer customer, Shop shop, Order.OrderStatus status, int index) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime receivedAt = now.minusDays(6 - (index % 6)).minusHours(index % 8);

        Order order = new Order();
        order.setBusinessId(businessId);
        order.setShopId(shop.getId());
        order.setCustomerId(customer.getId());
        order.setOrderNumber(IdGenerator.generateOrderId());
        order.setTrackingNumber(IdGenerator.generateTrackingId());
        order.setStatus(status);
        order.setPriority(index % 5 == 0 ? Order.Priority.EXPRESS : Order.Priority.NORMAL);
        order.setReceivedAt(receivedAt);
        order.setExpectedReadyAt(receivedAt.plusHours(24));
        order.setPromisedDate(receivedAt.plusHours(30));

        int itemRows = 1 + (index % 3);
        BigDecimal total = BigDecimal.ZERO;
        int pieceCount = 0;
        List<OrderItem> items = new ArrayList<>();
        for (int n = 0; n < itemRows; n++) {
            ServiceType service = services.get((index + n) % services.size());
            GarmentType garment = garments.get((index + n) % garments.size());
            BigDecimal unitPrice = service.getDefaultPrice() != null
                    ? service.getDefaultPrice()
                    : new BigDecimal("500.00");
            int qty = 1 + ((index * 3 + n) % 2); // 1 or 2

            OrderItem item = new OrderItem();
            String itemQr = IdGenerator.generateQrCode("item");
            item.setOrder(order);
            item.setItemNumber(itemQr.substring(3));
            item.setBarcode(itemQr);
            item.setServiceType(service.getName());
            item.setGarmentType(garment.getName());
            item.setDescription(service.getName() + " - " + garment.getName());
            item.setQuantity(qty);
            item.setUnitPrice(unitPrice);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            item.setSubtotal(subtotal);
            item.setDiscount(BigDecimal.ZERO);
            item.setTotal(subtotal);
            item.setStatus(itemStatusFor(status));
            if (qty > 1) {
                String batchBase = IdGenerator.generateBatchBase();
                List<OrderItemUnit> units = new ArrayList<>();
                for (int u = 1; u <= qty; u++) {
                    OrderItemUnit unit = new OrderItemUnit();
                    unit.setOrderItem(item);
                    unit.setUnitNumber(u);
                    unit.setBarcode(IdGenerator.generateUnitBarcode(batchBase, u));
                    units.add(unit);
                }
                item.setUnits(units);
                item.setBarcode("QL-" + batchBase);
                item.setItemNumber(batchBase);
            }
            items.add(item);
            total = total.add(subtotal);
            pieceCount += qty;
        }
        order.setItems(items);
        order.setItemCount(pieceCount);
        order.setTotalAmount(total);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setBalanceDue(total);

        List<OrderTimeline> timeline = new ArrayList<>();
        for (Order.OrderStatus stage : stagesThrough(status)) {
            OrderTimeline t = new OrderTimeline();
            t.setOrder(order);
            t.setType("STATUS_CHANGE");
            t.setStatus(stage.name());
            t.setDescription("Order " + stage.name().toLowerCase().replace('_', ' '));
            t.setTimestamp(receivedAt.plusHours(offsetFor(stage)));
            t.setUserId(index % 2 == 0 ? washer1.getId() : frontDesk.getId());
            t.setUserName(index % 2 == 0 ? washer1.getFirstName() : frontDesk.getFirstName());
            timeline.add(t);
        }
        order.setTimeline(timeline);

        order = orderRepository.save(order);

        if (status == Order.OrderStatus.COMPLETED) {
            order.setCompletedAt(now.minusHours(index % 20));
            order.setActualReadyAt(now.minusHours(24 + (index % 20)));
        }

        applyPayment(order, customer, shop, status, index);
        orderRepository.save(order);

        // Keep customer metrics in sync so dashboards/RFM look realistic.
        if (status != Order.OrderStatus.CANCELLED && status != Order.OrderStatus.RETURNED) {
            customer.addOrder(order.getTotalAmount());
            customerRepository.save(customer);
        }

        if (status.ordinal() >= Order.OrderStatus.QUALITY_CHECK.ordinal()
                && status != Order.OrderStatus.CANCELLED && status != Order.OrderStatus.RETURNED) {
            seedQualityCheck(order, index);
        }
    }

    private void applyPayment(Order order, Customer customer, Shop shop, Order.OrderStatus status, int index) {
        if (status == Order.OrderStatus.CANCELLED || status == Order.OrderStatus.RETURNED) {
            return;
        }
        BigDecimal amount = order.getTotalAmount();
        if (status == Order.OrderStatus.READY_FOR_PICKUP || status == Order.OrderStatus.OUT_FOR_DELIVERY) {
            amount = amount.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.HALF_UP);
        }
        OrderPayment payment = new OrderPayment();
        payment.setBusinessId(businessId);
        payment.setOrderId(order.getId());
        payment.setCustomerId(customer.getId());
        payment.setShopId(shop.getId());
        payment.setAmount(amount);
        payment.setMethod(paymentMethods.get(index % paymentMethods.size()));
        payment.setReference("PMT-" + order.getOrderNumber());
        payment.setStatus("COMPLETED");
        payment.setPaidAt(order.getReceivedAt().plusHours(2));
        payment.setCollectedBy(index % 2 == 0 ? frontDesk.getId() : manager.getId());
        payment.setTip(BigDecimal.ZERO);
        orderPaymentRepository.save(payment);

        order.addPayment(amount);
    }

    private void seedQualityCheck(Order order, int index) {
        OrderItem firstItem = order.getItems().get(0);
        QualityCheck qc = new QualityCheck();
        qc.setBusinessId(businessId);
        qc.setOrderItemId(firstItem.getId());
        qc.setStatus("PASSED");
        qc.setCheckedBy(ironer1.getId());
        qc.setCheckedAt(order.getReceivedAt().plusHours(6));
        qc.setNotes("Item inspected — no defects found");
        qualityCheckRepository.save(qc);
    }

    private OrderItem.ItemStatus itemStatusFor(Order.OrderStatus orderStatus) {
        return switch (orderStatus) {
            case RECEIVED -> OrderItem.ItemStatus.RECEIVED;
            case WASHING -> OrderItem.ItemStatus.WASHING;
            case WASHED -> OrderItem.ItemStatus.WASHED;
            case IRONING -> OrderItem.ItemStatus.IRONING;
            case IRONED -> OrderItem.ItemStatus.IRONED;
            case QUALITY_CHECK -> OrderItem.ItemStatus.QUALITY_CHECK;
            case READY_FOR_PICKUP, OUT_FOR_DELIVERY, COMPLETED -> OrderItem.ItemStatus.COMPLETED;
            case RETURNED, CANCELLED -> OrderItem.ItemStatus.ISSUE_REPORTED;
            default -> OrderItem.ItemStatus.RECEIVED;
        };
    }

    private List<Order.OrderStatus> stagesThrough(Order.OrderStatus current) {
        Order.OrderStatus[] all = Order.OrderStatus.values();
        List<Order.OrderStatus> stages = new ArrayList<>();
        for (Order.OrderStatus s : all) {
            if (s == Order.OrderStatus.DRAFT) continue;
            stages.add(s);
            if (s == current) break;
        }
        return stages;
    }

    private int offsetFor(Order.OrderStatus stage) {
        return switch (stage) {
            case RECEIVED -> 1;
            case WASHING -> 5;
            case WASHED -> 9;
            case IRONING -> 13;
            case IRONED -> 17;
            case QUALITY_CHECK -> 21;
            case READY_FOR_PICKUP -> 25;
            case COMPLETED -> 30;
            default -> 1;
        };
    }

    // ---------------------------------------------------------------------
    // Expenses
    // ---------------------------------------------------------------------

    private void seedExpenses() {
        Object[][] data = {
                {ExpenseCategory.UTILITIES, "Monthly electricity bill", "185000.00", "EKEDC", "ELE-2026-081"},
                {ExpenseCategory.RENT, "Shop rent - East Branch", "750000.00", "Lagos State Gov", "RENT-081"},
                {ExpenseCategory.SUPPLIES, "Detergent restock", "185000.00", "Bulk Supplies Ltd", "INV-7721"},
                {ExpenseCategory.WAGES, "Staff weekly wages", "320000.00", "Staff Payroll", "PAY-042"},
                {ExpenseCategory.MARKETING, "Instagram ad campaign", "95000.00", "Meta Ads", "AD-5512"},
                {ExpenseCategory.TRANSPORT, "Pickup/delivery fuel", "28000.00", "Total Energies", "FUEL-013"},
                {ExpenseCategory.REPAIRS, "Washing machine service", "120000.00", "TechServ Ltd", "SVC-009"},
        };
        int i = 0;
        for (Object[] row : data) {
            Expense expense = new Expense();
            expense.setBusinessId(businessId);
            expense.setCategory((ExpenseCategory) row[0]);
            expense.setDescription((String) row[1]);
            expense.setAmount(new BigDecimal((String) row[2]));
            expense.setExpenseDate(LocalDate.now().minusDays(i++ % 14));
            expense.setPaidTo((String) row[3]);
            expense.setReference((String) row[4]);
            expense.setCreatedBy(manager.getId());
            expenseRepository.save(expense);
        }
        log.info("Seeded {} expenses.", expenseRepository.count());
    }

    // ---------------------------------------------------------------------
    // Shifts
    // ---------------------------------------------------------------------

    private void seedShifts() {
        Shop main = shops.get(0);
        createShift(washer1.getId(), main, "CHECKED_IN", 0, true);
        createShift(washer1.getId(), main, "CHECKED_OUT", 1, true);
        createShift(ironer1.getId(), main, "CHECKED_IN", 0, true);
        createShift(frontDesk.getId(), main, "CHECKED_IN", 0, false);
        createShift(manager.getId(), main, "CHECKED_OUT", 1, false);
        createShift(washer1.getId(), shops.get(1), "CHECKED_OUT", 2, false);
        log.info("Seeded employee shifts.");
    }

    private void createShift(UUID employeeId, Shop shop, String status, int daysAgo, boolean fullDay) {
        LocalDate date = LocalDate.now(seedZone).minusDays(daysAgo);
        EmployeeShift shift = new EmployeeShift();
        shift.setBusinessId(businessId);
        shift.setEmployeeId(employeeId);
        shift.setShopId(shop.getId());
        shift.setDate(date);
        shift.setScheduledStart(date.atTime(8, 0));
        shift.setScheduledEnd(date.atTime(fullDay ? 17 : 13, 0));
        if (!"CHECKED_IN".equals(status)) {
            shift.setActualStart(date.atTime(8, 5));
            shift.setActualEnd(date.atTime(fullDay ? 17 : 13, 0));
        } else {
            shift.setActualStart(date.atTime(8, 5));
            shift.setLastActivityAt(LocalDateTime.now(seedZone));
        }
        shift.setStatus(EmployeeShift.ShiftStatus.valueOf(status));
        shift.setTotalWorkMinutes(fullDay ? 535 : 295);
        shift.setTotalBreakMinutes(30);
        shift.setOvertimeMinutes(0);
        shift.setNotes("Seeded demo shift");
        employeeShiftRepository.save(shift);
    }

    // ---------------------------------------------------------------------

    private void logCredentials() {
        log.info("==========================================================");
        log.info(" Demo tenant login credentials (password: {})", DEMO_PASSWORD);
        log.info("   business slug : {}", DEMO_SLUG);
        log.info("   owner         : owner   @{}", DEMO_EMAIL_DOMAIN);
        log.info("   manager       : manager @{}", DEMO_EMAIL_DOMAIN);
        log.info("   front desk    : frontdesk @{}", DEMO_EMAIL_DOMAIN);
        log.info("   washer        : washer1, washer2 @{}", DEMO_EMAIL_DOMAIN);
        log.info("   ironer        : ironer1 @{}", DEMO_EMAIL_DOMAIN);
        log.info("   delivery      : delivery1 @{}", DEMO_EMAIL_DOMAIN);
        log.info("==========================================================");
    }
}

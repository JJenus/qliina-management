package com.jjenus.qliina_management.payment.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.customer.dto.CustomerSummaryDTO;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import com.jjenus.qliina_management.identity.repository.ShopRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.dto.OrderSummaryDTO;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.model.OrderItem;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.payment.dto.*;
import com.jjenus.qliina_management.payment.model.*;
import com.jjenus.qliina_management.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjenus.qliina_management.identity.model.User;


import com.jjenus.qliina_management.notification.model.SMSConfiguration;
import com.jjenus.qliina_management.common.security.EncryptionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final OrderPaymentRepository paymentRepository;
    private final CashDrawerSessionRepository cashDrawerRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final CorporateAccountRepository corporateAccountRepository;
    private final InvoiceRepository invoiceRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    private final EncryptionService encryptionService;
    
    private UUID getCurrentUserId() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("User " + userId.toString().substring(0, 8));
    }
    
    @Transactional(readOnly = true)
    public PageResponse<PaymentDTO> listPayments(UUID businessId, PaymentFilter filter, Pageable pageable) {
        Page<OrderPayment> page = paymentRepository.findByFilters(
            businessId,
            filter != null ? filter.getOrderId() : null,
            filter != null ? filter.getCustomerId() : null,
            filter != null ? filter.getShopId() : null,
            filter != null ? filter.getMethod() : null,
            filter != null ? filter.getStatus() : null,
            filter != null ? filter.getFromDate() : null,
            filter != null ? filter.getToDate() : null,
            filter != null ? filter.getCollectedBy() : null,
            pageable
        );
        
        return PageResponse.from(page.map(this::mapToPaymentDTO));
    }
    
    @Transactional(readOnly = true)
    public PaymentDetailDTO getPayment(UUID paymentId) {
        OrderPayment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException("Payment not found", "PAYMENT_NOT_FOUND"));
        return mapToPaymentDetailDTO(payment);
    }
    
    @Transactional
    public PaymentResultDTO processPayment(UUID businessId, UUID orderId, ProcessPaymentRequest request) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found", "ORDER_NOT_FOUND"));
        
        // Validate payment method
        PaymentMethod method = paymentMethodRepository.findByBusinessIdAndType(businessId, request.getMethod())
            .orElseThrow(() -> new BusinessException("Invalid payment method", "INVALID_PAYMENT_METHOD"));
        
        BigDecimal amount = BigDecimal.valueOf(request.getAmount());
        
        // Create payment record
        OrderPayment payment = new OrderPayment();
        payment.setBusinessId(businessId);
        payment.setShopId(order.getShopId());
        payment.setOrderId(orderId);
        payment.setCustomerId(order.getCustomerId());
        payment.setAmount(amount);
        payment.setMethod(request.getMethod());
        payment.setReference(request.getReference());
        payment.setStatus("COMPLETED");
        payment.setPaidAt(LocalDateTime.now());
        payment.setCollectedBy(getCurrentUserId());
        
        if (request.getTip() != null) {
            payment.setTip(BigDecimal.valueOf(request.getTip()));
        }
        
        if ("CASH".equals(request.getMethod()) && request.getCashReceived() != null) {
            BigDecimal cashReceived = BigDecimal.valueOf(request.getCashReceived());
            if (cashReceived.compareTo(amount) > 0) {
                payment.setChangeAmount(cashReceived.subtract(amount));
            }
        }
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        if (request.getCardDetails() != null) {
            metadata.put("cardLast4", request.getCardDetails().getLast4());
            metadata.put("cardBrand", request.getCardDetails().getBrand());
            metadata.put("cardToken", request.getCardDetails().getToken());
        }
        if (request.getWalletDetails() != null) {
            metadata.put("walletProvider", request.getWalletDetails().getProvider());
            metadata.put("walletTransactionId", request.getWalletDetails().getTransactionId());
        }
        payment.setMetadata(metadata);
        
        payment = paymentRepository.save(payment);
        
        // Update cash drawer if cash payment
        if ("CASH".equals(request.getMethod())) {
            updateCashDrawer(businessId, order.getShopId(), payment);
        }
        
        // Calculate new balance
        BigDecimal totalPaid = paymentRepository.sumPaymentsByOrderId(orderId);
        BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        
        boolean isFullyPaid = totalPaid.compareTo(orderTotal) >= 0;
        BigDecimal balanceDue = isFullyPaid ? BigDecimal.ZERO : orderTotal.subtract(totalPaid);
        
        // Update order paid amount
        order.setPaidAmount(totalPaid);
        order.setBalanceDue(balanceDue);
        orderRepository.save(order);
        
        return PaymentResultDTO.builder()
            .success(true)
            .paymentId(payment.getId())
            .amount(payment.getAmount())
            .status(payment.getStatus())
            .balanceDue(balanceDue)
            .isFullyPaid(isFullyPaid)
            .transactionId(payment.getReference())
            .receiptUrl(generateReceiptUrl(payment))
            .change(payment.getChangeAmount())
            .build();
    }
    
    @Transactional
    public PaymentResultDTO splitPayment(UUID businessId, UUID orderId, SplitPaymentRequest request) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<String> errors = new ArrayList<>();
        List<PaymentResultDTO> results = new ArrayList<>();
        
        for (SplitPaymentRequest.PaymentSplit split : request.getPayments()) {
            try {
                ProcessPaymentRequest paymentRequest = new ProcessPaymentRequest();
                paymentRequest.setAmount(split.getAmount());
                paymentRequest.setMethod(split.getMethod());
                paymentRequest.setReference(split.getReference());
                paymentRequest.setTip(split.getTip());
                
                PaymentResultDTO result = processPayment(businessId, orderId, paymentRequest);
                results.add(result);
                totalAmount = totalAmount.add(BigDecimal.valueOf(split.getAmount()));
            } catch (Exception e) {
                errors.add("Failed to process " + split.getMethod() + " payment: " + e.getMessage());
            }
        }
        
        boolean allSuccess = errors.isEmpty();
        Order order = orderRepository.findById(orderId).orElseThrow();
        BigDecimal orderTotal = order.getTotalAmount();
        boolean isFullyPaid = totalAmount.compareTo(orderTotal) >= 0;
        BigDecimal balanceDue = isFullyPaid ? BigDecimal.ZERO : orderTotal.subtract(totalAmount);
        
        return PaymentResultDTO.builder()
            .success(allSuccess)
            .amount(totalAmount)
            .status(allSuccess ? "COMPLETED" : "PARTIAL")
            .balanceDue(balanceDue)
            .isFullyPaid(isFullyPaid)
            .errors(errors)
            .build();
    }
    
    @Transactional
    public RefundResultDTO processRefund(UUID businessId, UUID paymentId, RefundRequest request) {
        OrderPayment originalPayment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException("Payment not found", "PAYMENT_NOT_FOUND"));
        
        BigDecimal refundAmount = BigDecimal.valueOf(request.getAmount());
        if (originalPayment.getAmount().compareTo(refundAmount) < 0) {
            throw new BusinessException("Refund amount exceeds payment amount", "INVALID_REFUND_AMOUNT");
        }
        
        // Check if payment is already fully refunded
        BigDecimal totalRefunded = refundRepository.sumRefundsByPaymentId(paymentId);
        if (totalRefunded == null) totalRefunded = BigDecimal.ZERO;
        
        if (totalRefunded.add(refundAmount).compareTo(originalPayment.getAmount()) > 0) {
            throw new BusinessException("Total refund would exceed payment amount", "REFUND_LIMIT_EXCEEDED");
        }
        
        Refund refund = new Refund();
        refund.setPayment(originalPayment);
        refund.setAmount(refundAmount);
        refund.setReason(request.getReason());
        refund.setReasonCode(request.getReasonCode());
        refund.setMethod(request.getMethod() != null ? request.getMethod() : "ORIGINAL");
        refund.setStatus("COMPLETED");
        refund.setProcessedAt(LocalDateTime.now());
        refund.setProcessedBy(getCurrentUserId());
        refund.setNotes(request.getNotes());
        refund.setApprovalCode(request.getApprovalCode() != null ? request.getApprovalCode() : generateApprovalCode());
        refund.setBusinessId(businessId);
        
        refund = refundRepository.save(refund);
        
        // Update payment status
        BigDecimal newTotalRefunded = totalRefunded.add(refundAmount);
        if (newTotalRefunded.compareTo(originalPayment.getAmount()) >= 0) {
            originalPayment.setStatus("REFUNDED");
        } else {
            originalPayment.setStatus("PARTIALLY_REFUNDED");
        }
        paymentRepository.save(originalPayment);
        
        // Update order balance
        Order order = orderRepository.findById(originalPayment.getOrderId()).orElseThrow();
        BigDecimal totalPaid = paymentRepository.sumPaymentsByOrderId(order.getId());
        order.setPaidAmount(totalPaid);
        order.setBalanceDue(order.getTotalAmount().subtract(totalPaid));
        orderRepository.save(order);
        
        // If cash refund, update cash drawer
        if ("CASH".equals(refund.getMethod())) {
            updateCashDrawerForRefund(businessId, originalPayment.getShopId(), refund);
        }
        
        return RefundResultDTO.builder()
            .refundId(refund.getId())
            .originalPaymentId(paymentId)
            .amount(refund.getAmount())
            .status(refund.getStatus())
            .processedAt(refund.getProcessedAt())
            .receiptUrl(generateRefundReceiptUrl(refund))
            .newBalance(originalPayment.getAmount().subtract(newTotalRefunded))
            .build();
    }
    
    @Transactional(readOnly = true)
    public List<PaymentMethodDTO> getPaymentMethods(UUID businessId) {
        return paymentMethodRepository.findByBusinessIdAndIsActiveTrue(businessId).stream()
            .map(method -> PaymentMethodDTO.builder()
                .id(method.getId())
                .name(method.getName())
                .type(method.getType())
                .icon(method.getIcon())
                .isActive(method.getIsActive())
                .requiresReference(method.getRequiresReference())
                .surcharge(method.getSurcharge() != null ? method.getSurcharge().doubleValue() : null)
                .minAmount(method.getMinAmount() != null ? method.getMinAmount().doubleValue() : null)
                .maxAmount(method.getMaxAmount() != null ? method.getMaxAmount().doubleValue() : null)
                .build())
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public PageResponse<CashDrawerSessionDTO> getCashDrawerSessions(UUID businessId, UUID shopId, String status, 
                                                                     LocalDateTime fromDate, LocalDateTime toDate, 
                                                                     Pageable pageable) {
        Page<CashDrawerSession> page = cashDrawerRepository.findByFilters(
            businessId, shopId, status, fromDate, toDate, pageable);
        return PageResponse.from(page.map(this::mapToDrawerSessionDTO));
    }
    
    @Transactional
    public CashDrawerSessionDTO openCashDrawer(UUID businessId, OpenDrawerRequest request) {
        // Check if there's already an open session for this shop
        Optional<CashDrawerSession> existingOpen = cashDrawerRepository.findOpenSession(request.getShopId());
        if (existingOpen.isPresent()) {
            throw new BusinessException("Cash drawer already open for this shop", "DRAWER_ALREADY_OPEN");
        }
        
        CashDrawerSession session = new CashDrawerSession();
        session.setBusinessId(businessId);
        session.setShopId(request.getShopId());
        session.setOpenedBy(getCurrentUserId());
        session.setOpenedAt(LocalDateTime.now());
        session.setStartingCash(BigDecimal.valueOf(request.getStartingCash()));
        session.setStatus("OPEN");
        session.setNotes(request.getNotes());
        
        session = cashDrawerRepository.save(session);
        
        return mapToDrawerSessionDTO(session);
    }
    
    @Transactional
    public CashDrawerSessionDTO closeCashDrawer(UUID businessId, CloseDrawerRequest request) {
        CashDrawerSession session = cashDrawerRepository.findById(request.getSessionId())
            .orElseThrow(() -> new BusinessException("Session not found", "SESSION_NOT_FOUND"));
        
        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException("Session is not open", "SESSION_NOT_OPEN");
        }
        
        // Calculate expected cash
        LocalDateTime openedAt = session.getOpenedAt();
        LocalDateTime now = LocalDateTime.now();
        
        BigDecimal totalSales = paymentRepository.sumCashPaymentsByDateRange(
            session.getShopId(), openedAt, now);
        if (totalSales == null) totalSales = BigDecimal.ZERO;
        
        BigDecimal totalRefunds = refundRepository.sumCashRefundsByDateRange(
            session.getShopId(), openedAt, now);
        if (totalRefunds == null) totalRefunds = BigDecimal.ZERO;
        
        BigDecimal expectedCash = session.getStartingCash()
            .add(totalSales)
            .subtract(totalRefunds);
        
        BigDecimal actualCash = BigDecimal.valueOf(request.getActualCash());
        
        session.setExpectedClosingCash(expectedCash);
        session.setActualClosingCash(actualCash);
        session.setDifference(expectedCash.subtract(actualCash));
        session.setClosedAt(now);
        session.setClosedBy(getCurrentUserId());
        
        // Determine status based on difference
        BigDecimal difference = session.getDifference().abs();
        if (difference.compareTo(BigDecimal.valueOf(0.01)) <= 0) {
            session.setStatus("RECONCILED");
        } else if (difference.compareTo(BigDecimal.valueOf(5.00)) <= 0) {
            session.setStatus("DISCREPANCY");
        } else {
            session.setStatus("DISCREPANCY");
            // Could trigger alert for large discrepancies
        }
        
        session.setNotes(request.getNotes());
        session = cashDrawerRepository.save(session);
        
        return mapToDrawerSessionDTO(session);
    }
    
    @Transactional
    public CorporateAccountDTO createCorporateAccount(UUID businessId, CreateCorporateAccountRequest request) {
        // Check if customer already has an account
        Optional<CorporateAccount> existing = corporateAccountRepository.findByCustomerId(request.getCustomerId());
        if (existing.isPresent()) {
            throw new BusinessException("Customer already has a corporate account", "ACCOUNT_EXISTS");
        }
        
        Customer customer = customerRepository.findById(request.getCustomerId())
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        CorporateAccount account = new CorporateAccount();
        account.setBusinessId(businessId);
        account.setCustomerId(request.getCustomerId());
        account.setCompanyName(request.getCompanyName());
        account.setTaxId(request.getTaxId());
        account.setCreditLimit(BigDecimal.valueOf(request.getCreditLimit()));
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setPaymentTerms(request.getPaymentTerms());
        account.setBillingCycle(request.getBillingCycle());
        account.setStatus("ACTIVE");
        
        //todo: marker to confirm BillingAddress request and account + CorporateAccount
        if (request.getBillingAddress() != null) {
            CorporateAccount.BillingAddress billingAddress = new CorporateAccount.BillingAddress();
            billingAddress.setAddressLine1(request.getBillingAddress().getAddressLine1());
            billingAddress.setAddressLine2(request.getBillingAddress().getAddressLine2());
            billingAddress.setCity(request.getBillingAddress().getCity());
            billingAddress.setState(request.getBillingAddress().getState());
            billingAddress.setPostalCode(request.getBillingAddress().getPostalCode());
            billingAddress.setCountry(request.getBillingAddress().getCountry());
            account.setBillingAddress(billingAddress);
        }
        
        account = corporateAccountRepository.save(account);
        
        // Save contacts if any
        if (request.getContacts() != null && !request.getContacts().isEmpty()) {
            // In a real implementation, you'd save contacts to a separate table
        }
        
        return mapToCorporateAccountDTO(account);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<CorporateAccountDTO> listCorporateAccounts(UUID businessId, String status, Pageable pageable) {
        Page<CorporateAccount> page;
        if (status != null) {
            page = corporateAccountRepository.findByBusinessIdAndStatus(businessId, status, pageable);
        } else {
            page = corporateAccountRepository.findByBusinessId(businessId, pageable);
        }
        return PageResponse.from(page.map(this::mapToCorporateAccountDTO));
    }
    
    @Transactional(readOnly = true)
    public CorporateAccountDTO getCorporateAccount(UUID accountId) {
        CorporateAccount account = corporateAccountRepository.findById(accountId)
            .orElseThrow(() -> new BusinessException("Account not found", "ACCOUNT_NOT_FOUND"));
        return mapToCorporateAccountDTO(account);
    }
    
    @Transactional
    public InvoiceDTO generateInvoice(UUID businessId, UUID accountId, GenerateInvoiceRequest request) {
        CorporateAccount account = corporateAccountRepository.findById(accountId)
            .orElseThrow(() -> new BusinessException("Account not found", "ACCOUNT_NOT_FOUND"));
        
        // Get orders to invoice
        List<Order> orders;
        if (request.getIncludeOrders() != null && !request.getIncludeOrders().isEmpty()) {
            orders = orderRepository.findAllById(request.getIncludeOrders());
        } else {
            orders = orderRepository.findUnpaidOrdersByCustomerAndDateRange(
                account.getCustomerId(),
                request.getPeriodStart().atStartOfDay(),
                request.getPeriodEnd().atTime(23, 59, 59)
            );
        }
        
        if (orders.isEmpty()) {
            throw new BusinessException("No orders to invoice", "NO_ORDERS");
        }
        
        Invoice invoice = new Invoice();
        invoice.setBusinessId(businessId);
        invoice.setInvoiceNumber(generateInvoiceNumber(businessId));
        invoice.setAccountId(accountId);
        invoice.setCompanyName(account.getCompanyName());
        invoice.setPeriodStart(request.getPeriodStart());
        invoice.setPeriodEnd(request.getPeriodEnd());
        invoice.setDueDate(request.getDueDate());
        invoice.setStatus("DRAFT");
        
        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        
        for (Order order : orders) {
            if (order.getTotalAmount() == null) continue;
            
            InvoiceItem item = new InvoiceItem();
            item.setInvoice(invoice);
            item.setOrderId(order.getId());
            item.setOrderNumber(order.getOrderNumber());
            item.setOrderDate(order.getCreatedAt());
            item.setAmount(order.getTotalAmount());
            item.setStatus("PENDING");
            items.add(item);
            subtotal = subtotal.add(order.getTotalAmount());
        }
        
        invoice.setItems(items);
        invoice.setSubtotal(subtotal);
        invoice.setTax(calculateTax(subtotal, businessId));
        invoice.setTotal(subtotal.add(invoice.getTax()));
        
        invoice = invoiceRepository.save(invoice);
        
        // Update orders with invoice reference
        for (Order order : orders) {
            order.setInvoiceId(invoice.getId());
            orderRepository.save(order);
        }
        
        account.setLastInvoiceDate(LocalDateTime.now());
        corporateAccountRepository.save(account);
        
        if (request.getSendEmail() != null && request.getSendEmail()) {
            // Send email with invoice
            sendInvoiceEmail(account, invoice);
        }
        
        return mapToInvoiceDTO(invoice);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<InvoiceDTO> listInvoices(UUID businessId, UUID accountId, String status, 
                                                   LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Page<Invoice> page = invoiceRepository.findByFilters(
            businessId, accountId, status, fromDate, toDate, pageable);
        return PageResponse.from(page.map(this::mapToInvoiceDTO));
    }
    
    @Transactional
    public void processOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<Invoice> overdueInvoices = invoiceRepository.findByStatusAndDueDateBefore("SENT", today);
        
        for (Invoice invoice : overdueInvoices) {
            invoice.setStatus("OVERDUE");
            invoiceRepository.save(invoice);
            
            // Could trigger notifications here
        }
    }
    
    private void updateCashDrawer(UUID businessId, UUID shopId, OrderPayment payment) {
        Optional<CashDrawerSession> openSession = cashDrawerRepository.findOpenSession(shopId);
        if (openSession.isPresent()) {
            CashDrawerSession session = openSession.get();
            // In a real implementation, you'd add a transaction record
            log.info("Cash payment added to session: {} for amount: {}", session.getId(), payment.getAmount());
        }
    }
    
    private void updateCashDrawerForRefund(UUID businessId, UUID shopId, Refund refund) {
        Optional<CashDrawerSession> openSession = cashDrawerRepository.findOpenSession(shopId);
        if (openSession.isPresent()) {
            CashDrawerSession session = openSession.get();
            log.info("Cash refund processed in session: {} for amount: {}", session.getId(), refund.getAmount());
        }
    }
    
    public BigDecimal sumRevenueByDateRange(UUID businessId, UUID shopId, LocalDateTime startDate, LocalDateTime endDate) {
    return paymentRepository.sumRevenueByDateRange(businessId, shopId, startDate, endDate);
}
    //this send methods will be removed
    private void sendViaTwilio(SMSConfiguration config, String to, String message) {
        // Twilio implementation
        try {
            String accountSid = encryptionService.decrypt(config.getAccountSidEncrypted());
            String authToken = encryptionService.decrypt(config.getAuthTokenEncrypted());
            
            // In a real implementation, you'd use Twilio SDK
            log.info("Sending Twilio SMS from {} to: {} - Message: {}", config.getFromNumber(), to, message);
            
            // Simulate API call
            // Twilio.init(accountSid, authToken);
            // Message.creator(new PhoneNumber(to), new PhoneNumber(config.getFromNumber()), message).create();
            
        } catch (Exception e) {
            log.error("Failed to send Twilio SMS", e);
            throw new BusinessException("Failed to send SMS via Twilio: " + e.getMessage(), "SMS_SEND_FAILED");
        }
    }
    
    private void sendViaAWSSNS(SMSConfiguration config, String to, String message) {
        // AWS SNS implementation
        try {
            // In a real implementation, you'd use AWS SDK
            log.info("Sending AWS SNS SMS to: {} - Message: {}", to, message);
            
            // AmazonSNS snsClient = AmazonSNSClient.builder().build();
            // PublishRequest request = new PublishRequest()
            //     .withMessage(message)
            //     .withPhoneNumber(to);
            // snsClient.publish(request);
            
        } catch (Exception e) {
            log.error("Failed to send AWS SNS SMS", e);
            throw new BusinessException("Failed to send SMS via AWS SNS: " + e.getMessage(), "SMS_SEND_FAILED");
        }
    }
    
    private void sendViaVonage(SMSConfiguration config, String to, String message) {
        // Vonage (formerly Nexmo) implementation
        try {
            // In a real implementation, you'd use Vonage SDK
            log.info("Sending Vonage SMS to: {} - Message: {}", to, message);
            
            // VonageClient client = VonageClient.builder()
            //     .apiKey(apiKey)
            //     .apiSecret(apiSecret)
            //     .build();
            // TextMessage message = new TextMessage(config.getFromNumber(), to, message);
            // client.getSmsClient().sendMessage(message);
            
        } catch (Exception e) {
            log.error("Failed to send Vonage SMS", e);
            throw new BusinessException("Failed to send SMS via Vonage: " + e.getMessage(), "SMS_SEND_FAILED");
        }
    }
    
    private PaymentDTO mapToPaymentDTO(OrderPayment payment) {
        String orderNumber = "";
        try {
            Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null) {
                orderNumber = order.getOrderNumber();
            }
        } catch (Exception e) {
            log.error("Error fetching order number", e);
        }
        
        return PaymentDTO.builder()
            .id(payment.getId())
            .orderId(payment.getOrderId())
            .orderNumber(orderNumber)
            .amount(payment.getAmount())
            .method(payment.getMethod())
            .reference(payment.getReference())
            .status(payment.getStatus())
            .paidAt(payment.getPaidAt())
            .collectedBy(PaymentDTO.UserInfo.builder()
                .id(payment.getCollectedBy())
                .name(getUserName(payment.getCollectedBy()))
                .build())
            .tip(payment.getTip())
            .change(payment.getChangeAmount())
            .metadata(payment.getMetadata())
            .build();
    }
    
    private PaymentDetailDTO mapToPaymentDetailDTO(OrderPayment payment) {
        PaymentDTO base = mapToPaymentDTO(payment);
        
        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        Customer customer = order != null ? 
            customerRepository.findById(order.getCustomerId()).orElse(null) : null;
        
        PaymentDetailDTO.OrderDetailsDTO orderDetails = null;
        if (order != null && customer != null) {
            CustomerSummaryDTO customerSummary = CustomerSummaryDTO.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phone(customer.getPhone())
                .build();
            
            List<PaymentDetailDTO.OrderItemSummaryDTO> items = order.getItems().stream()
                .map(item -> PaymentDetailDTO.OrderItemSummaryDTO.builder()
                    .id(item.getId())
                    .serviceType(item.getServiceType())
                    .quantity(item.getQuantity())
                    .subtotal(item.getSubtotal())
                    .build())
                .collect(Collectors.toList());
            
            orderDetails = PaymentDetailDTO.OrderDetailsDTO.builder()
                .customer(customerSummary)
                .items(items)
                .total(order.getTotalAmount())
                .build();
        }
        
        List<RefundDTO> refunds = refundRepository.findByPaymentId(payment.getId()).stream()
            .map(r -> RefundDTO.builder()
                .id(r.getId())
                .amount(r.getAmount())
                .reason(r.getReason())
                .processedAt(r.getProcessedAt())
                .processedBy(r.getProcessedBy() != null ? getUserName(r.getProcessedBy()) : null)
                .status(r.getStatus())
                .build())
            .collect(Collectors.toList());
        
        return PaymentDetailDTO.builder()
            .id(base.getId())
            .orderId(base.getOrderId())
            .orderNumber(base.getOrderNumber())
            .amount(base.getAmount())
            .method(base.getMethod())
            .reference(base.getReference())
            .status(base.getStatus())
            .paidAt(base.getPaidAt())
            .collectedBy(base.getCollectedBy())
            .tip(base.getTip())
            .change(base.getChange())
            .metadata(base.getMetadata())
            .orderDetails(orderDetails)
            .refunds(refunds)
            .build();
    }
    
    private CashDrawerSessionDTO mapToDrawerSessionDTO(CashDrawerSession session) {
        String shopName = shopRepository.findById(session.getShopId())
            .map(s -> s.getName())
            .orElse("");
        
        List<CashDrawerSessionDTO.TransactionDTO> transactions = new ArrayList<>();
        // Would fetch from transaction table if implemented
        
        return CashDrawerSessionDTO.builder()
            .id(session.getId())
            .shopId(session.getShopId())
            .shopName(shopName)
            .openedBy(CashDrawerSessionDTO.UserInfo.builder()
                .id(session.getOpenedBy())
                .name(getUserName(session.getOpenedBy()))
                .build())
            .openedAt(session.getOpenedAt())
            .closedAt(session.getClosedAt())
            .closedBy(session.getClosedBy() != null ? 
                CashDrawerSessionDTO.UserInfo.builder()
                    .id(session.getClosedBy())
                    .name(getUserName(session.getClosedBy()))
                    .build() : null)
            .startingCash(session.getStartingCash())
            .expectedClosingCash(session.getExpectedClosingCash())
            .actualClosingCash(session.getActualClosingCash())
            .difference(session.getDifference())
            .status(session.getStatus())
            .transactions(transactions)
            .notes(session.getNotes())
            .build();
    }
    
    private CorporateAccountDTO mapToCorporateAccountDTO(CorporateAccount account) {
    BigDecimal availableCredit = account.getCreditLimit().subtract(
        account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO);
    
    // Convert BillingAddress to AddressDTO
    com.jjenus.qliina_management.common.AddressDTO addressDTO = null;
    if (account.getBillingAddress() != null) {
        addressDTO = com.jjenus.qliina_management.common.AddressDTO.builder()
            .addressLine1(account.getBillingAddress().getAddressLine1())
            .addressLine2(account.getBillingAddress().getAddressLine2())
            .city(account.getBillingAddress().getCity())
            .state(account.getBillingAddress().getState())
            .postalCode(account.getBillingAddress().getPostalCode())
            .country(account.getBillingAddress().getCountry())
            .build();
    }
    
    return CorporateAccountDTO.builder()
        .id(account.getId())
        .customerId(account.getCustomerId())
        .companyName(account.getCompanyName())
        .taxId(account.getTaxId())
        .creditLimit(account.getCreditLimit())
        .currentBalance(account.getCurrentBalance())
        .availableCredit(availableCredit)
        .paymentTerms(account.getPaymentTerms())
        .billingCycle(account.getBillingCycle())
        .billingAddress(addressDTO)  // ← Now passing AddressDTO, not BillingAddress
        .contacts(new ArrayList<>())
        .status(account.getStatus())
        .lastInvoiceDate(account.getLastInvoiceDate())
        .lastPaymentDate(account.getLastPaymentDate())
        .build();
}
    
    private InvoiceDTO mapToInvoiceDTO(Invoice invoice) {
        return InvoiceDTO.builder()
            .id(invoice.getId())
            .invoiceNumber(invoice.getInvoiceNumber())
            .accountId(invoice.getAccountId())
            .companyName(invoice.getCompanyName())
            .period(InvoiceDTO.PeriodDTO.builder()
                .start(invoice.getPeriodStart())
                .end(invoice.getPeriodEnd())
                .build())
            .dueDate(invoice.getDueDate())
            .items(invoice.getItems().stream()
                .map(item -> InvoiceDTO.InvoiceItemDTO.builder()
                    .orderId(item.getOrderId())
                    .orderNumber(item.getOrderNumber())
                    .orderDate(item.getOrderDate())
                    .amount(item.getAmount())
                    .status(item.getStatus())
                    .build())
                .collect(Collectors.toList()))
            .subtotal(invoice.getSubtotal())
            .tax(invoice.getTax())
            .total(invoice.getTotal())
            .status(invoice.getStatus())
            .pdfUrl(invoice.getPdfUrl())
            .sentAt(invoice.getSentAt())
            .paidAt(invoice.getPaidAt())
            .build();
    }
    
    private String generateReceiptUrl(OrderPayment payment) {
        return String.format("/api/v1/receipts/%s", payment.getId());
    }
    
    private String generateRefundReceiptUrl(Refund refund) {
        return String.format("/api/v1/refunds/%s/receipt", refund.getId());
    }
    
    private String generateInvoiceNumber(UUID businessId) {
        String year = String.valueOf(LocalDate.now().getYear());
        String month = String.format("%02d", LocalDate.now().getMonthValue());
        String sequence = String.format("%06d", new Random().nextInt(1000000));
        return String.format("INV-%s-%s-%s", year, month, sequence);
    }
    
    private String generateApprovalCode() {
        return "APP" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
    
    private BigDecimal calculateTax(BigDecimal amount, UUID businessId) {
        //todo: fetch tax rate from business config
        return amount.multiply(BigDecimal.valueOf(0.1)); // 10% default tax
    }
    
    private void sendInvoiceEmail(CorporateAccount account, Invoice invoice) {
        log.info("Sending invoice {} to {}", invoice.getInvoiceNumber(), account.getCompanyName());
        // In a real implementation, use email service
    }
}

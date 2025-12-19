package com.jjenus.qliina_management.payment.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.payment.dto.*;
import com.jjenus.qliina_management.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Payment Management", description = "Payment processing and management endpoints")
@RestController
@RequestMapping("/api/v1/{businessId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ==================== Payment Operations ====================

    @Operation(
        summary = "List payments",
        description = "Get paginated list of payments with optional filters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved payments"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters")
    })
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<PageResponse<PaymentDTO>> listPayments(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Payment filters")
            @ModelAttribute PaymentFilter filter,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "paidAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(paymentService.listPayments(businessId, filter, pageable));
    }

    @Operation(
        summary = "Get payment details",
        description = "Get detailed information about a specific payment"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved payment"),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<PaymentDetailDTO> getPayment(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Payment ID", required = true)
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    @Operation(
        summary = "Process payment",
        description = "Process a payment for an order"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid payment request"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/orders/{orderId}/process")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.process')")
    public ResponseEntity<PaymentResultDTO> processPayment(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Valid @RequestBody ProcessPaymentRequest request) {
        return ResponseEntity.ok(paymentService.processPayment(businessId, orderId, request));
    }

    @Operation(
        summary = "Split payment",
        description = "Process a split payment using multiple payment methods"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Split payment processed"),
        @ApiResponse(responseCode = "400", description = "Invalid payment split"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/orders/{orderId}/split")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.process')")
    public ResponseEntity<PaymentResultDTO> splitPayment(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Order ID", required = true)
            @PathVariable UUID orderId,
            
            @Valid @RequestBody SplitPaymentRequest request) {
        return ResponseEntity.ok(paymentService.splitPayment(businessId, orderId, request));
    }

    @Operation(
        summary = "Process refund",
        description = "Process a refund for a payment"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Refund processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid refund request"),
        @ApiResponse(responseCode = "404", description = "Payment not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.refund')")
    public ResponseEntity<RefundResultDTO> processRefund(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Payment ID", required = true)
            @PathVariable UUID paymentId,
            
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.processRefund(businessId, paymentId, request));
    }

    @Operation(
        summary = "Get payment methods",
        description = "Get list of available payment methods"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved payment methods"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/methods")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<List<PaymentMethodDTO>> getPaymentMethods(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(paymentService.getPaymentMethods(businessId));
    }

    // ==================== Cash Drawer Operations ====================

    @Operation(
        summary = "List cash drawer sessions",
        description = "Get paginated list of cash drawer sessions"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved sessions"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/drawer-sessions")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<PageResponse<CashDrawerSessionDTO>> getCashDrawerSessions(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Shop ID")
            @RequestParam(required = false) UUID shopId,
            
            @Parameter(description = "Session status")
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            
            @Parameter(description = "End date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            
            @PageableDefault(size = 20, sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(paymentService.getCashDrawerSessions(
            businessId, shopId, status, fromDate, toDate, pageable));
    }

    @Operation(
        summary = "Open cash drawer",
        description = "Open a new cash drawer session"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cash drawer opened successfully"),
        @ApiResponse(responseCode = "400", description = "Drawer already open"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/drawer-sessions/open")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.process')")
    public ResponseEntity<CashDrawerSessionDTO> openCashDrawer(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody OpenDrawerRequest request) {
        return ResponseEntity.ok(paymentService.openCashDrawer(businessId, request));
    }

    @Operation(
        summary = "Close cash drawer",
        description = "Close an existing cash drawer session with reconciliation"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cash drawer closed successfully"),
        @ApiResponse(responseCode = "404", description = "Session not found"),
        @ApiResponse(responseCode = "400", description = "Session not open or invalid"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/drawer-sessions/close")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.process')")
    public ResponseEntity<CashDrawerSessionDTO> closeCashDrawer(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CloseDrawerRequest request) {
        return ResponseEntity.ok(paymentService.closeCashDrawer(businessId, request));
    }

    // ==================== Corporate Account Operations ====================

    @Operation(
        summary = "Create corporate account",
        description = "Create a new corporate account for a customer"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Corporate account created"),
        @ApiResponse(responseCode = "400", description = "Account already exists"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/corporate-accounts")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.update')")
    public ResponseEntity<CorporateAccountDTO> createCorporateAccount(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateCorporateAccountRequest request) {
        return ResponseEntity.ok(paymentService.createCorporateAccount(businessId, request));
    }

    @Operation(
        summary = "List corporate accounts",
        description = "Get paginated list of corporate accounts"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved accounts"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/corporate-accounts")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.view')")
    public ResponseEntity<PageResponse<CorporateAccountDTO>> listCorporateAccounts(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Account status filter")
            @RequestParam(required = false) String status,
            
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(paymentService.listCorporateAccounts(businessId, status, pageable));
    }

    @Operation(
        summary = "Get corporate account",
        description = "Get corporate account details by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved account"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/corporate-accounts/{accountId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.view')")
    public ResponseEntity<CorporateAccountDTO> getCorporateAccount(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Account ID", required = true)
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(paymentService.getCorporateAccount(accountId));
    }

    // ==================== Invoice Operations ====================

    @Operation(
        summary = "Generate invoice",
        description = "Generate an invoice for a corporate account"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Invoice generated successfully"),
        @ApiResponse(responseCode = "404", description = "Account or orders not found"),
        @ApiResponse(responseCode = "400", description = "No orders to invoice"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/corporate-accounts/{accountId}/invoices/generate")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.process')")
    public ResponseEntity<InvoiceDTO> generateInvoice(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Account ID", required = true)
            @PathVariable UUID accountId,
            
            @Valid @RequestBody GenerateInvoiceRequest request) {
        return ResponseEntity.ok(paymentService.generateInvoice(businessId, accountId, request));
    }

    @Operation(
        summary = "List invoices",
        description = "Get paginated list of invoices"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved invoices"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/invoices")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'payment.view')")
    public ResponseEntity<PageResponse<InvoiceDTO>> listInvoices(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Account ID filter")
            @RequestParam(required = false) UUID accountId,
            
            @Parameter(description = "Invoice status filter")
            @RequestParam(required = false) String status,
            
            @Parameter(description = "Start date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @Parameter(description = "End date filter")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(paymentService.listInvoices(
            businessId, accountId, status, fromDate, toDate, pageable));
    }

    @Operation(
        summary = "Process overdue invoices",
        description = "Manually trigger processing of overdue invoices"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Overdue invoices processed"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping("/invoices/process-overdue")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'admin.settings')")
    public ResponseEntity<SuccessResponse> processOverdueInvoices(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        paymentService.processOverdueInvoices();
        return ResponseEntity.ok(SuccessResponse.of("Overdue invoices processed successfully"));
    }
}
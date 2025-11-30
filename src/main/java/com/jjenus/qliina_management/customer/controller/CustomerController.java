package com.jjenus.qliina_management.customer.controller;

import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.common.SuccessResponse;
import com.jjenus.qliina_management.common.ErrorResponse;
import com.jjenus.qliina_management.customer.dto.*;
import com.jjenus.qliina_management.customer.service.CustomerService;
import com.jjenus.qliina_management.order.dto.OrderSummaryDTO;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Customer Management", description = "Complete customer management endpoints for managing customer data, loyalty, and analytics")
@RestController
@RequestMapping("/api/v1/{businessId}/customers")
@RequiredArgsConstructor
public class CustomerController {
    
    private final CustomerService customerService;
    
    // ==================== Basic CRUD Operations ====================
    
    @Operation(
        summary = "List customers",
        description = "Get paginated list of customers with optional filters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customers"),
        @ApiResponse(responseCode = "403", description = "Access denied", 
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.view')")
    public ResponseEntity<PageResponse<CustomerSummaryDTO>> listCustomers(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            
            @Parameter(description = "Filter criteria for customers")
            @ModelAttribute CustomerFilter filter) {
        return ResponseEntity.ok(customerService.listCustomers(businessId, filter, pageable));
    }
    
    @Operation(
        summary = "Search customers",
        description = "Search customers by query string with pagination"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved search results"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid search parameters",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/search")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.view')")
    public ResponseEntity<PageResponse<CustomerSummaryDTO>> searchCustomers(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute CustomerSearchRequest request) {
        return ResponseEntity.ok(customerService.searchCustomers(businessId, request));
    }
    
    @Operation(
        summary = "Get customer",
        description = "Get detailed customer information by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customer"),
        @ApiResponse(responseCode = "404", description = "Customer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{customerId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.view')")
    public ResponseEntity<CustomerDetailDTO> getCustomer(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Customer ID", required = true)
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(customerService.getCustomer(customerId));
    }
    
    @Operation(
        summary = "Create customer",
        description = "Create a new customer with optional addresses, preferences, and notes"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid customer data or duplicate phone",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.create')")
    public ResponseEntity<CustomerDetailDTO> createCustomer(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(customerService.createCustomer(businessId, request));
    }
    
    @Operation(
        summary = "Update customer",
        description = "Update an existing customer's information"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer updated successfully"),
        @ApiResponse(responseCode = "404", description = "Customer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid update data or duplicate phone",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{customerId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.update')")
    public ResponseEntity<CustomerDetailDTO> updateCustomer(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Customer ID", required = true)
            @PathVariable UUID customerId,
            
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(customerId, request));
    }
    
    @Operation(
        summary = "Delete customer",
        description = "Soft delete a customer (marks as inactive)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Customer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{customerId}")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.delete')")
    public ResponseEntity<SuccessResponse> deleteCustomer(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Customer ID", required = true)
            @PathVariable UUID customerId) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.ok(SuccessResponse.of("Customer deleted successfully"));
    }
    
    // ==================== Customer Orders ====================
    
    @Operation(
        summary = "Get customer orders",
        description = "Get paginated list of orders for a specific customer"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved orders"),
        @ApiResponse(responseCode = "404", description = "Customer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{customerId}/orders")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'order.view')")
    public ResponseEntity<PageResponse<OrderSummaryDTO>> getCustomerOrders(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Customer ID", required = true)
            @PathVariable UUID customerId,
            
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(customerService.getCustomerOrders(customerId, pageable));
    }
    
    // ==================== Loyalty Management ====================
    
    @Operation(
        summary = "Get customer loyalty",
        description = "Get loyalty details including points, tier, history, and available rewards"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved loyalty info"),
        @ApiResponse(responseCode = "404", description = "Customer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{customerId}/loyalty")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.view')")
    public ResponseEntity<CustomerLoyaltyDTO> getCustomerLoyalty(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Customer ID", required = true)
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(customerService.getCustomerLoyalty(customerId));
    }
    
    @Operation(
        summary = "Adjust loyalty points",
        description = "Manually adjust loyalty points for a customer (add or deduct)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Points adjusted successfully"),
        @ApiResponse(responseCode = "404", description = "Customer not found",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Insufficient points for deduction",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{customerId}/loyalty/adjust")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.update')")
    public ResponseEntity<CustomerLoyaltyDTO> adjustPoints(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "Customer ID", required = true)
            @PathVariable UUID customerId,
            
            @Valid @RequestBody AdjustPointsRequest request) {
        return ResponseEntity.ok(customerService.adjustPoints(customerId, request));
    }
    
    // ==================== Analytics & Reporting ====================
    
    @Operation(
        summary = "Get top customers",
        description = "Get top customers by various metrics (spend, frequency, AOV)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved top customers"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/top")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<List<TopCustomerDTO>> getTopCustomers(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute TopCustomersRequest request) {
        return ResponseEntity.ok(customerService.getTopCustomers(businessId, request));
    }
    
    @Operation(
        summary = "Get RFM segments",
        description = "Get RFM (Recency, Frequency, Monetary) segment distribution for all customers"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved RFM segments"),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/segments/rfm")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.view.operational')")
    public ResponseEntity<RFMSegmentsDTO> getRFMSegments(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(customerService.getRFMSegments(businessId));
    }
    
    // ==================== Import/Export Operations ====================
    
    @Operation(
        summary = "Import customers",
        description = "Import customers from CSV or Excel file"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Import completed with results"),
        @ApiResponse(responseCode = "400", description = "Invalid file format or data",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/import")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'customer.create')")
    public ResponseEntity<ImportResultDTO> importCustomers(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Parameter(description = "CSV/Excel file to import", required = true)
            @RequestParam("file") MultipartFile file,
            
            @Parameter(description = "Update existing customers if found")
            @RequestParam(value = "updateExisting", defaultValue = "false") Boolean updateExisting,
            
            @Parameter(description = "JSON field mapping configuration")
            @RequestParam(value = "mapping", required = false) String mapping) {
        
        CustomerImportRequest request = new CustomerImportRequest();
        request.setFile(file);
        request.setUpdateExisting(updateExisting);
        // Parse mapping JSON if provided
        return ResponseEntity.ok(customerService.importCustomers(businessId, request));
    }
    
    @Operation(
        summary = "Export customers",
        description = "Export customers to CSV or Excel file based on filter criteria"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "File download started"),
        @ApiResponse(responseCode = "400", description = "Invalid export parameters",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/export")
    @PreAuthorize("hasPermission(#businessId, 'BUSINESS', 'report.export')")
    public ResponseEntity<byte[]> exportCustomers(
            @Parameter(description = "Business ID", required = true)
            @PathVariable UUID businessId,
            
            @Valid @ModelAttribute CustomerExportRequest request) {
        // Implementation would generate and return file
        return ResponseEntity.ok(new byte[0]);
    }
}
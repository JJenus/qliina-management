package com.jjenus.qliina_management.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDTO {
    private UUID id;
    private String poNumber;
    private UUID supplierId;
    private String supplierName;
    private UUID shopId;
    private String shopName;
    private String status;
    private LocalDateTime orderDate;
    private LocalDate expectedDelivery;
    private LocalDateTime deliveredAt;
    private String deliveredBy;
    private List<PurchaseOrderItemDTO> items;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal shipping;
    private BigDecimal total;
    private String notes;
    private String terms;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderItemDTO {
        private UUID id;
        private UUID itemId;
        private String itemName;
        private String itemSku;
        private Integer quantity;
        private Integer receivedQuantity;
        private BigDecimal unitPrice;
        private BigDecimal total;
        private String notes;
    }
}

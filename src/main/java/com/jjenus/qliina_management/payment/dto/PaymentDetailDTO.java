package com.jjenus.qliina_management.payment.dto;

import com.jjenus.qliina_management.customer.dto.CustomerSummaryDTO;
import com.jjenus.qliina_management.order.dto.OrderSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentDetailDTO extends PaymentDTO {
    private OrderDetailsDTO orderDetails;
    private List<RefundDTO> refunds;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderDetailsDTO {
        private CustomerSummaryDTO customer;
        private List<OrderItemSummaryDTO> items;
        private BigDecimal total;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummaryDTO {
        private UUID id;
        private String serviceType;
        private Integer quantity;
        private BigDecimal subtotal;
    }
}

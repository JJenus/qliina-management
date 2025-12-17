package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerSessionDTO {
    private UUID id;
    private UUID shopId;
    private String shopName;
    private UserInfo openedBy;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private UserInfo closedBy;
    private BigDecimal startingCash;
    private BigDecimal expectedClosingCash;
    private BigDecimal actualClosingCash;
    private BigDecimal difference;
    private String status;
    private List<TransactionDTO> transactions;
    private String notes;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private UUID id;
        private String name;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDTO {
        private UUID id;
        private String type;
        private BigDecimal amount;
        private String reference;
        private LocalDateTime timestamp;
        private UUID userId;
    }
}

package com.jjenus.qliina_management.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDTO {
    private UUID id;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime processedAt;
    private String processedBy;
    private String status;
}

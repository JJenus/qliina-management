package com.jjenus.qliina_management.order.dto;

import com.jjenus.qliina_management.common.AddressDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderDetailDTO extends OrderSummaryDTO {
    private List<ItemDTO> items;
    private List<DiscountDTO> discounts;
    private List<PaymentDTO> payments;
    private DeliveryInfoDTO delivery;
    private List<OrderNoteDTO> notes;
    private List<AttachmentDTO> attachments;
    private List<TimelineEventDTO> timeline;
    private MetadataDTO metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDTO {
        private UUID id;
        private String itemNumber;
        private String barcode;
        private String serviceType;
        private String garmentType;
        private String description;
        private Integer quantity;
        private BigDecimal weight;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private BigDecimal discount;
        private BigDecimal total;
        private String status;
        private String specialInstructions;
        private List<String> images;
        private List<ItemStatusHistoryDTO> statusHistory;
        private QualityCheckDTO qualityCheck;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemStatusHistoryDTO {
        private String status;
        private LocalDateTime timestamp;
        private String updatedBy;
        private String notes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityCheckDTO {
        private String status;
        private String checkedBy;
        private LocalDateTime checkedAt;
        private List<DefectDTO> defects;
        private String notes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefectDTO {
        private UUID id;
        private String type;
        private String severity;
        private String description;
        private List<String> images;
        private String reportedBy;
        private LocalDateTime reportedAt;
        private String status;
        private String resolution;
        private BigDecimal compensation;
        private String compensationType;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscountDTO {
        private String type;
        private Double value;
        private String reason;
        private String promoCode;
        private String approvedBy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDTO {
        private UUID id;
        private BigDecimal amount;
        private String method;
        private String reference;
        private String status;
        private LocalDateTime paidAt;
        private String collectedBy;
        private BigDecimal tip;
        private BigDecimal change;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfoDTO {
        private String type;
        private AddressDTO address;
        private LocalDateTime scheduledTime;
        private LocalDateTime deliveredAt;
        private String deliveredBy;
        private String proofOfDelivery;
        private String recipientName;
        private String notes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderNoteDTO {
        private UUID id;
        private String content;
        private String type;
        private String createdBy;
        private LocalDateTime createdAt;
        private Boolean isCustomerVisible;
        private List<String> attachments;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDTO {
        private UUID id;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private String url;
        private String uploadedBy;
        private LocalDateTime uploadedAt;
        private String type;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataDTO {
        private UUID createdBy;
        private LocalDateTime createdAt;
        private UUID updatedBy;
        private LocalDateTime updatedAt;
        private String ipAddress;
        private String userAgent;
    }
}

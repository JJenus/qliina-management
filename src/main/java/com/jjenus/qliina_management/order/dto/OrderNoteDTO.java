package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderNoteDTO {
    private UUID id;
    private String content;
    private String type;
    private String createdBy;
    private LocalDateTime createdAt;
    private Boolean isCustomerVisible;
    private List<String> attachments;
}

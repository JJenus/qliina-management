package com.jjenus.qliina_management.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    private UUID id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String url;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private String type;
}

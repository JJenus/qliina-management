package com.jjenus.qliina_management.notification.dto;

import lombok.Data;

@Data
public class UpdateTemplateRequest {
    private String name;
    private String description;
    private String subject;
    private String titleTemplate;
    private String bodyTemplate;
    private Boolean isActive;
}

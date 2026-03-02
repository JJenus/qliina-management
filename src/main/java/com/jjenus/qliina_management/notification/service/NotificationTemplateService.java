package com.jjenus.qliina_management.notification.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.notification.dto.CreateTemplateRequest;
import com.jjenus.qliina_management.notification.dto.NotificationTemplateDTO;
import com.jjenus.qliina_management.notification.dto.UpdateTemplateRequest;
import com.jjenus.qliina_management.notification.model.Notification;
import com.jjenus.qliina_management.notification.model.NotificationTemplate;
import com.jjenus.qliina_management.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {
    
    private final NotificationTemplateRepository templateRepository;
    
    @Transactional(readOnly = true)
    public List<NotificationTemplateDTO> getTemplates(UUID businessId) {
        return templateRepository.findByBusinessId(businessId).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public NotificationTemplateDTO createTemplate(UUID businessId, CreateTemplateRequest request) {
        NotificationTemplate template = new NotificationTemplate();
        template.setBusinessId(businessId);
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setType(Notification.NotificationType.valueOf(request.getType()));
        template.setChannel(Notification.NotificationChannel.valueOf(request.getChannel()));
        template.setSubject(request.getSubject());
        template.setTitleTemplate(request.getTitleTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        template.setVariables(request.getVariables());
        template.setIsActive(true);
        
        template = templateRepository.save(template);
        return mapToDTO(template);
    }
    
    @Transactional
    public NotificationTemplateDTO updateTemplate(UUID templateId, UpdateTemplateRequest request) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
        
        if (request.getName() != null) template.setName(request.getName());
        if (request.getDescription() != null) template.setDescription(request.getDescription());
        if (request.getSubject() != null) template.setSubject(request.getSubject());
        if (request.getTitleTemplate() != null) template.setTitleTemplate(request.getTitleTemplate());
        if (request.getBodyTemplate() != null) template.setBodyTemplate(request.getBodyTemplate());
        if (request.getIsActive() != null) template.setIsActive(request.getIsActive());
        
        template = templateRepository.save(template);
        return mapToDTO(template);
    }
    
    @Transactional
    public void deleteTemplate(UUID templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new BusinessException("Template not found", "TEMPLATE_NOT_FOUND"));
        templateRepository.delete(template);
    }
    
    private NotificationTemplateDTO mapToDTO(NotificationTemplate template) {
        return NotificationTemplateDTO.builder()
            .id(template.getId())
            .name(template.getName())
            .description(template.getDescription())
            .type(template.getType().toString())
            .channel(template.getChannel().toString())
            .subject(template.getSubject())
            .titleTemplate(template.getTitleTemplate())
            .bodyTemplate(template.getBodyTemplate())
            .variables(template.getVariables())
            .isActive(template.getIsActive())
            .createdAt(template.getCreatedAt())
            .build();
    }
}

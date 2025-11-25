package com.jjenus.qliina_management.audit.service;

import com.jjenus.qliina_management.audit.dto.ConsentDTO;
import com.jjenus.qliina_management.audit.dto.ConsentRequest;
import com.jjenus.qliina_management.audit.model.ConsentRecord;
import com.jjenus.qliina_management.audit.repository.ConsentRecordRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {
    
    private final ConsentRecordRepository consentRepository;
    private final CustomerRepository customerRepository;
    
    @Transactional(readOnly = true)
    public List<ConsentDTO> getCustomerConsents(UUID customerId) {
        return consentRepository.findByCustomerId(customerId).stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public ConsentDTO getActiveConsent(UUID customerId, String consentType) {
        ConsentRecord.ConsentType type = ConsentRecord.ConsentType.valueOf(consentType);
        return consentRepository.findActiveConsent(customerId, type)
            .map(this::mapToDTO)
            .orElse(null);
    }
    
    @Transactional
    public ConsentDTO recordConsent(UUID businessId, UUID customerId, ConsentRequest request) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new BusinessException("Customer not found", "CUSTOMER_NOT_FOUND"));
        
        HttpServletRequest httpRequest = getCurrentHttpRequest();
        
        ConsentRecord.ConsentType type = ConsentRecord.ConsentType.valueOf(request.getConsentType());
        
        // Check for existing active consent
        consentRepository.findActiveConsent(customerId, type)
            .ifPresent(existing -> {
                // Revoke previous consent
                existing.setStatus(ConsentRecord.ConsentStatus.SUPERSEDED);
                consentRepository.save(existing);
            });
        
        ConsentRecord consent = new ConsentRecord();
        consent.setBusinessId(businessId);
        consent.setCustomerId(customerId);
        consent.setConsentType(type);
        consent.setGranted(request.getGranted());
        consent.setGrantedAt(LocalDateTime.now());
        consent.setIpAddress(httpRequest != null ? httpRequest.getRemoteAddr() : null);
        consent.setUserAgent(httpRequest != null ? httpRequest.getHeader("User-Agent") : null);
        consent.setConsentVersion(request.getConsentVersion());
        consent.setStatus(ConsentRecord.ConsentStatus.ACTIVE);
        
        if (request.getGranted()) {
            // Set expiry (e.g., 2 years from now)
            consent.setExpiresAt(LocalDateTime.now().plusYears(2));
        }
        
        consent = consentRepository.save(consent);
        
        return mapToDTO(consent);
    }
    
    @Transactional
    public void revokeConsent(UUID customerId, String consentType, UUID revokedBy) {
        ConsentRecord.ConsentType type = ConsentRecord.ConsentType.valueOf(consentType);
        
        consentRepository.findActiveConsent(customerId, type)
            .ifPresent(consent -> {
                HttpServletRequest request = getCurrentHttpRequest();
                
                consent.revoke(
                    request != null ? request.getRemoteAddr() : null,
                    revokedBy
                );
                
                consentRepository.save(consent);
            });
    }
    
    @Transactional(readOnly = true)
    public boolean hasConsent(UUID customerId, String consentType) {
        ConsentRecord.ConsentType type = ConsentRecord.ConsentType.valueOf(consentType);
        return consentRepository.findActiveConsent(customerId, type)
            .map(ConsentRecord::getGranted)
            .orElse(false);
    }
    
    @Scheduled(cron = "0 0 3 * * *") // Run at 3 AM daily
    @Transactional
    public void processExpiredConsents() {
        log.info("Processing expired consents");
        
        List<ConsentRecord> expiredConsents = consentRepository.findExpiredConsents(LocalDateTime.now());
        
        for (ConsentRecord consent : expiredConsents) {
            consent.setStatus(ConsentRecord.ConsentStatus.EXPIRED);
            consent.setGranted(false);
            consentRepository.save(consent);
        }
        
        log.info("Processed {} expired consents", expiredConsents.size());
    }
    
    public Map<String, Object> getConsentStats(UUID businessId) {
    List<Object[]> stats = consentRepository.getConsentStats(businessId);
    Map<String, Object> result = new HashMap<>();
    for (Object[] stat : stats) {
        result.put((String) stat[0], stat[1]);
    }
    return result;
}
    
    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    private ConsentDTO mapToDTO(ConsentRecord consent) {
        return ConsentDTO.builder()
            .id(consent.getId())
            .customerId(consent.getCustomerId())
            .consentType(consent.getConsentType().toString())
            .granted(consent.getGranted())
            .grantedAt(consent.getGrantedAt())
            .ipAddress(consent.getIpAddress())
            .userAgent(consent.getUserAgent())
            .revokedAt(consent.getRevokedAt())
            .consentVersion(consent.getConsentVersion())
            .expiresAt(consent.getExpiresAt())
            .status(consent.getStatus().toString())
            .build();
    }
}

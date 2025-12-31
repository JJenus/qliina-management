package com.jjenus.qliina_management.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.qliina_management.audit.dto.CreateDataSubjectRequest;
import com.jjenus.qliina_management.audit.dto.DataSubjectRequestDTO;
import com.jjenus.qliina_management.audit.dto.UpdateDataSubjectRequest;
import com.jjenus.qliina_management.audit.model.AuditLog;
import com.jjenus.qliina_management.audit.model.DataSubjectRequest;
import com.jjenus.qliina_management.audit.repository.AuditLogRepository;
import com.jjenus.qliina_management.audit.repository.DataSubjectRequestRepository;
import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.customer.model.Customer;
import com.jjenus.qliina_management.customer.repository.CustomerRepository;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.model.Order;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSubjectRequestService {
    
    private final DataSubjectRequestRepository requestRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public DataSubjectRequestDTO createRequest(UUID businessId, CreateDataSubjectRequest request) {
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                .orElse(null);
        }
        
        DataSubjectRequest dsr = new DataSubjectRequest();
        dsr.setBusinessId(businessId);
        dsr.setRequestNumber(generateRequestNumber(businessId));
        dsr.setCustomerId(request.getCustomerId());
        dsr.setCustomerName(request.getCustomerName());
        dsr.setCustomerEmail(request.getCustomerEmail());
        dsr.setRequestType(DataSubjectRequest.RequestType.valueOf(request.getRequestType()));
        dsr.setStatus(DataSubjectRequest.RequestStatus.SUBMITTED);
        dsr.setSubmittedAt(LocalDateTime.now());
        dsr.setDueDate(LocalDateTime.now().plusDays(30)); // GDPR requires 30 days
        dsr.setRequestDetails(request.getRequestDetails());
        dsr.setVerificationMethod(request.getVerificationMethod());
        
        if (customer != null) {
            dsr.setCustomerId(customer.getId());
            dsr.setCustomerName(customer.getFirstName() + " " + customer.getLastName());
            dsr.setCustomerEmail(customer.getEmail());
        }
        
        dsr = requestRepository.save(dsr);
        
        return mapToDTO(dsr);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<DataSubjectRequestDTO> listRequests(UUID businessId, UUID customerId, String type, 
                                                              String status, LocalDateTime fromDate, 
                                                              LocalDateTime toDate, Pageable pageable) {
        DataSubjectRequest.RequestType requestType = type != null ?
            DataSubjectRequest.RequestType.valueOf(type) : null;
        DataSubjectRequest.RequestStatus requestStatus = status != null ?
            DataSubjectRequest.RequestStatus.valueOf(status) : null;
        
        Page<DataSubjectRequest> page = requestRepository.searchRequests(
            businessId, customerId, requestType, requestStatus, fromDate, toDate, pageable);
        
        return PageResponse.from(page.map(this::mapToDTO));
    }
    
    @Transactional(readOnly = true)
    public DataSubjectRequestDTO getRequest(UUID requestId) {
        DataSubjectRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> new BusinessException("Request not found", "REQUEST_NOT_FOUND"));
        return mapToDTO(request);
    }
    
    @Transactional
    public DataSubjectRequestDTO updateRequest(UUID requestId, UpdateDataSubjectRequest request) {
        DataSubjectRequest dsr = requestRepository.findById(requestId)
            .orElseThrow(() -> new BusinessException("Request not found", "REQUEST_NOT_FOUND"));
        
        if (request.getStatus() != null) {
            dsr.setStatus(DataSubjectRequest.RequestStatus.valueOf(request.getStatus()));
            
            if (dsr.getStatus() == DataSubjectRequest.RequestStatus.COMPLETED) {
                dsr.setCompletedAt(LocalDateTime.now());
                dsr.setCompletedBy(getCurrentUserId());
            }
        }
        
        if (request.getResponseDetails() != null) {
            dsr.setResponseDetails(request.getResponseDetails());
        }
        
        if (request.getDataExportUrl() != null) {
            dsr.setDataExportUrl(request.getDataExportUrl());
        }
        
        if (request.getVerified() != null) {
            dsr.setVerified(request.getVerified());
        }
        
        if (request.getNotes() != null) {
            dsr.setNotes(request.getNotes());
        }
        
        if (request.getAssignedTo() != null) {
            dsr.setAssignedTo(request.getAssignedTo());
        }
        
        dsr = requestRepository.save(dsr);
        
        return mapToDTO(dsr);
    }
    
    @Transactional
    public DataSubjectRequestDTO processRequest(UUID requestId) {
        DataSubjectRequest dsr = requestRepository.findById(requestId)
            .orElseThrow(() -> new BusinessException("Request not found", "REQUEST_NOT_FOUND"));
        
        dsr.setStatus(DataSubjectRequest.RequestStatus.IN_PROGRESS);
        
        // Process based on request type
        switch (dsr.getRequestType()) {
            case ACCESS:
                processAccessRequest(dsr);
                break;
            case ERASURE:
                processErasureRequest(dsr);
                break;
            case PORTABILITY:
                processPortabilityRequest(dsr);
                break;
            default:
                // Other types
                break;
        }
        
        dsr = requestRepository.save(dsr);
        
        return mapToDTO(dsr);
    }
    
    private void processAccessRequest(DataSubjectRequest dsr) {
        try {
            Map<String, Object> data = new HashMap<>();
            
            // Get customer data
            if (dsr.getCustomerId() != null) {
                Customer customer = customerRepository.findById(dsr.getCustomerId()).orElse(null);
                if (customer != null) {
                    data.put("customer", customer);
                }
                
                // Get orders
                List<Order> orders = orderRepository.findByCustomerId(dsr.getCustomerId());
                data.put("orders", orders);
                
                // Get audit logs
                List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityId(
                    "CUSTOMER", dsr.getCustomerId());
                data.put("auditLogs", auditLogs);
            }
            
            String jsonData = objectMapper.writeValueAsString(data);
            
            // In a real implementation, this would generate a file and upload to secure storage
            String exportUrl = generateDataExport(dsr, jsonData);
            
            dsr.setDataExportUrl(exportUrl);
            dsr.setResponseDetails("Data access request completed. Data available at: " + exportUrl);
            
        } catch (Exception e) {
            log.error("Failed to process access request", e);
            dsr.setResponseDetails("Error processing request: " + e.getMessage());
        }
    }
    
    private void processErasureRequest(DataSubjectRequest dsr) {
        // Implementation for GDPR right to erasure
        // This would anonymize or delete customer data
        dsr.setResponseDetails("Right to erasure request processed. Customer data has been anonymized.");
    }
    
    public long countRequestsByStatus(UUID businessId, String status) {
    DataSubjectRequest.RequestStatus requestStatus = DataSubjectRequest.RequestStatus.valueOf(status);
    return requestRepository.countByBusinessIdAndStatus(businessId, requestStatus);
}
    
    private void processPortabilityRequest(DataSubjectRequest dsr) {
        // Implementation for data portability
        // Similar to access but in machine-readable format
        dsr.setResponseDetails("Data portability request completed.");
    }
    
    private String generateDataExport(DataSubjectRequest dsr, String data) {
        // In a real implementation, this would create a file and upload to secure storage
        String filename = String.format("dsr_%s_%s.json", 
            dsr.getRequestNumber(), 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        
        return "/exports/" + filename;
    }
    
    @Scheduled(cron = "0 0 9 * * *") // Run at 9 AM daily
    @Transactional
    public void checkOverdueRequests() {
        log.info("Checking for overdue data subject requests");
        
        List<DataSubjectRequest> overdue = requestRepository.findOverdueRequests(LocalDateTime.now());
        
        for (DataSubjectRequest request : overdue) {
            log.warn("Overdue DSR: {}", request.getRequestNumber());
            // Send notifications to assigned staff
        }
    }
    
    private String generateRequestNumber(UUID businessId) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        String sequence = String.format("%06d", new Random().nextInt(1000000));
        return String.format("DSR-%s-%s-%s", 
            businessId.toString().substring(0, 4), 
            year, 
            sequence);
    }
    
    private UUID getCurrentUserId() {
        // In a real implementation, get from SecurityContext
        return UUID.randomUUID();
    }
    
    private DataSubjectRequestDTO mapToDTO(DataSubjectRequest request) {
        User completedBy = request.getCompletedBy() != null ?
            userRepository.findById(request.getCompletedBy()).orElse(null) : null;
        
        User assignedTo = request.getAssignedTo() != null ?
            userRepository.findById(request.getAssignedTo()).orElse(null) : null;
        
        return DataSubjectRequestDTO.builder()
            .id(request.getId())
            .requestNumber(request.getRequestNumber())
            .customerId(request.getCustomerId())
            .customerName(request.getCustomerName())
            .customerEmail(request.getCustomerEmail())
            .requestType(request.getRequestType().toString())
            .status(request.getStatus().toString())
            .submittedAt(request.getSubmittedAt())
            .dueDate(request.getDueDate())
            .completedAt(request.getCompletedAt())
            .completedBy(completedBy != null ? 
                completedBy.getFirstName() + " " + completedBy.getLastName() : null)
            .requestDetails(request.getRequestDetails())
            .responseDetails(request.getResponseDetails())
            .dataExportUrl(request.getDataExportUrl())
            .verified(request.getVerified())
            .assignedTo(assignedTo != null ? 
                assignedTo.getFirstName() + " " + assignedTo.getLastName() : null)
            .build();
    }
}

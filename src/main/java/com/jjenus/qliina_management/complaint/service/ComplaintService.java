package com.jjenus.qliina_management.complaint.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.complaint.dto.ComplaintDTO;
import com.jjenus.qliina_management.complaint.dto.CreateComplaintRequest;
import com.jjenus.qliina_management.complaint.dto.UpdateComplaintStatusRequest;
import com.jjenus.qliina_management.complaint.model.Complaint;
import com.jjenus.qliina_management.complaint.model.ComplaintSeverity;
import com.jjenus.qliina_management.complaint.model.ComplaintStatus;
import com.jjenus.qliina_management.complaint.repository.ComplaintRepository;
import com.jjenus.qliina_management.complaint.repository.ComplaintSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tenant + platform complaint workflows.
 *
 * <p>Tenant side ({@code businessId} scoped) is permission-gated per endpoint
 * (complaint.view / complaint.create / complaint.resolve). Platform side is
 * cross-tenant and gated by platform.complaints.view / platform.complaints.manage.
 * RESOLVED / REJECTED are terminal and require a resolution note.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintService {

    private static final List<ComplaintStatus> TERMINAL =
            List.of(ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED);

    private final ComplaintRepository complaintRepository;

    // ------------------------------------------------------------------
    // Tenant side
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ComplaintDTO> list(
            UUID businessId, ComplaintStatus status, ComplaintSeverity severity, int page, int size) {
        Page<Complaint> result = complaintRepository.findAll(
                ComplaintSpecifications.tenantFilter(businessId, status, severity),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.from(result.map(this::toDTO));
    }

    @Transactional(readOnly = true)
    public ComplaintDTO get(UUID businessId, UUID complaintId) {
        return toDTO(findOwned(businessId, complaintId));
    }

    @Transactional
    public ComplaintDTO create(UUID businessId, CreateComplaintRequest req) {
        Complaint complaint = new Complaint();
        complaint.setBusinessId(businessId);
        complaint.setShopId(req.getShopId());
        complaint.setCustomerName(req.getCustomerName().trim());
        complaint.setCategory(req.getCategory());
        complaint.setSeverity(req.getSeverity());
        complaint.setDescription(req.getDescription().trim());
        complaint.setStatus(ComplaintStatus.OPEN);
        complaint.setComplaintNumber(nextNumber(businessId));
        complaint.setSlaDueAt(LocalDateTime.now().plusHours(req.getSeverity().getSlaHours()));
        return toDTO(complaintRepository.save(complaint));
    }

    @Transactional
    public ComplaintDTO updateStatus(UUID businessId, UUID complaintId,
                                     UpdateComplaintStatusRequest req, UUID actorId) {
        Complaint complaint = findOwned(businessId, complaintId);
        applyTransition(complaint, req, actorId);
        return toDTO(complaintRepository.save(complaint));
    }

    // ------------------------------------------------------------------
    // Platform side (cross-tenant inbox)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ComplaintDTO> listPlatform(
            UUID businessId, ComplaintStatus status, ComplaintSeverity severity, int page, int size) {
        Page<Complaint> result = complaintRepository.findAll(
                ComplaintSpecifications.platformFilter(businessId, status, severity),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.from(result.map(this::toDTO));
    }

    @Transactional(readOnly = true)
    public ComplaintDTO getPlatform(UUID complaintId) {
        return toDTO(complaintRepository.findById(complaintId)
                .orElseThrow(() -> notFound()));
    }

    @Transactional
    public ComplaintDTO updateStatusPlatform(UUID complaintId,
                                             UpdateComplaintStatusRequest req, UUID actorId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(ComplaintService::notFound);
        applyTransition(complaint, req, actorId);
        return toDTO(complaintRepository.save(complaint));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void applyTransition(Complaint complaint, UpdateComplaintStatusRequest req, UUID actorId) {
        if (req.getStatus() == null) {
            throw new BusinessException("'status' is required", "VALIDATION_ERROR", "status");
        }
        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus target = req.getStatus();

        if (TERMINAL.contains(current) && target != current) {
            throw new BusinessException(
                    "Complaint is already " + current + " and cannot be reopened",
                    "INVALID_STATUS_TRANSITION", "status");
        }
        if (TERMINAL.contains(target)) {
            if (req.getResolutionNote() == null || req.getResolutionNote().isBlank()) {
                throw new BusinessException(
                        "'resolutionNote' is required to close a complaint",
                        "VALIDATION_ERROR", "resolutionNote");
            }
            complaint.setStatus(target);
            complaint.setResolutionNote(req.getResolutionNote().trim());
            complaint.setResolvedAt(LocalDateTime.now());
            complaint.setResolvedBy(actorId);
            log.info("Complaint {} {} by {}", complaint.getComplaintNumber(), target, actorId);
        } else {
            // OPEN / IN_REVIEW triage moves (e.g. OPEN -> IN_REVIEW)
            complaint.setStatus(target);
        }
    }

    private Complaint findOwned(UUID businessId, UUID complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(ComplaintService::notFound);
        if (!complaint.getBusinessId().equals(businessId)) {
            // Do not leak existence across tenants.
            throw notFound();
        }
        return complaint;
    }

    private String nextNumber(UUID businessId) {
        long seq = complaintRepository.countByBusinessId(businessId) + 1;
        return String.format("CMP-%05d", seq);
    }

    private static BusinessException notFound() {
        return new BusinessException("Complaint not found", "COMPLAINT_NOT_FOUND");
    }

    public ComplaintDTO toDTO(Complaint c) {
        boolean overdue = (c.getStatus() == ComplaintStatus.OPEN
                || c.getStatus() == ComplaintStatus.IN_REVIEW)
                && c.getSlaDueAt() != null
                && c.getSlaDueAt().isBefore(LocalDateTime.now());
        return ComplaintDTO.builder()
                .id(c.getId())
                .businessId(c.getBusinessId())
                .shopId(c.getShopId())
                .complaintNumber(c.getComplaintNumber())
                .customerName(c.getCustomerName())
                .category(c.getCategory())
                .severity(c.getSeverity())
                .description(c.getDescription())
                .status(c.getStatus())
                .slaDueAt(c.getSlaDueAt())
                .overdue(overdue)
                .resolvedAt(c.getResolvedAt())
                .resolutionNote(c.getResolutionNote())
                .resolvedBy(c.getResolvedBy())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
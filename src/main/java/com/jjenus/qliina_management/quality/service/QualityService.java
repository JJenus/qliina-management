package com.jjenus.qliina_management.quality.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.quality.dto.*;
import com.jjenus.qliina_management.quality.model.*;
import com.jjenus.qliina_management.quality.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjenus.qliina_management.identity.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityService {
    
    private final QualityChecklistRepository checklistRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final DefectRepository defectRepository;
    private final UserRepository userRepository;
    
    private UUID getCurrentUserId() {
        try {
            UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return userRepository.findByUsername(userDetails.getUsername())
                .map(User::getId)
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getUserName(UUID userId) {
        if (userId == null) return "System";
        return userRepository.findById(userId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("User " + userId.toString().substring(0, 8));
    }
    
    @Transactional(readOnly = true)
    public List<QualityChecklistDTO> listChecklists(UUID businessId) {
        return checklistRepository.findByBusinessIdAndIsActiveTrue(businessId).stream()
            .map(this::mapToChecklistDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public QualityChecklistDTO getChecklist(UUID checklistId) {
        QualityChecklist checklist = checklistRepository.findById(checklistId)
            .orElseThrow(() -> new BusinessException("Checklist not found", "CHECKLIST_NOT_FOUND"));
        return mapToChecklistDTO(checklist);
    }
    
    @Transactional
    public QualityChecklistDTO createChecklist(UUID businessId, CreateChecklistRequest request) {
        QualityChecklist checklist = new QualityChecklist();
        checklist.setBusinessId(businessId);
        checklist.setName(request.getName());
        checklist.setServiceTypeId(request.getServiceTypeId());
        checklist.setGarmentTypeId(request.getGarmentTypeId());
        checklist.setIsActive(true);
        
        List<ChecklistItem> items = new ArrayList<>();
        int order = 1;
        for (CreateChecklistRequest.ItemDTO itemDto : request.getItems()) {
            ChecklistItem item = new ChecklistItem();
            item.setChecklist(checklist);
            item.setDescription(itemDto.getDescription());
            item.setRequired(itemDto.getRequired() != null ? itemDto.getRequired() : true);
            item.setItemOrder(order++);
            item.setFailureSeverity(itemDto.getFailureSeverity());
            items.add(item);
        }
        
        checklist.setItems(items);
        checklist = checklistRepository.save(checklist);
        
        return mapToChecklistDTO(checklist);
    }
    
    @Transactional
    public QualityChecklistDTO updateChecklist(UUID checklistId, UpdateChecklistRequest request) {
        QualityChecklist checklist = checklistRepository.findById(checklistId)
            .orElseThrow(() -> new BusinessException("Checklist not found", "CHECKLIST_NOT_FOUND"));
        
        if (request.getName() != null) {
            checklist.setName(request.getName());
        }
        
        checklist = checklistRepository.save(checklist);
        return mapToChecklistDTO(checklist);
    }
    
    @Transactional
    public QualityCheckResultDTO performQualityCheck(UUID businessId, UUID orderId, UUID itemId, QualityCheckRequest request) {
        QualityCheck check = new QualityCheck();
        check.setOrderItemId(itemId);
        check.setChecklistId(request.getChecklistId());
        check.setCheckedBy(getCurrentUserId());
        check.setCheckedAt(LocalDateTime.now());
        
        boolean allPassed = true;
        boolean anyFailed = false;
        
        List<CheckResult> results = new ArrayList<>();
        for (QualityCheckRequest.CheckResultDTO resultDto : request.getResults()) {
            CheckResult result = new CheckResult();
            result.setQualityCheck(check);
            result.setChecklistItemId(resultDto.getChecklistItemId());
            result.setPassed(resultDto.getPassed());
            result.setNotes(resultDto.getNotes());
            results.add(result);
            
            if (!resultDto.getPassed()) {
                allPassed = false;
                anyFailed = true;
            }
        }
        
        check.setResults(results);
        
        List<Defect> defects = new ArrayList<>();
        if (request.getDefects() != null) {
            for (ReportDefectRequest defectDto : request.getDefects()) {
                Defect defect = new Defect();
                defect.setQualityCheck(check);
                defect.setType(defectDto.getType());
                defect.setSeverity(defectDto.getSeverity());
                defect.setDescription(defectDto.getDescription());
                defect.setLocation(defectDto.getLocation());
                defect.setImages(defectDto.getImages() != null ? defectDto.getImages() : new ArrayList<>());
                defect.setReportedBy(getCurrentUserId());
                defect.setReportedAt(LocalDateTime.now());
                defect.setStatus("OPEN");
                defects.add(defect);
                anyFailed = true;
            }
        }
        
        check.setDefects(defects);
        
        if (allPassed) {
            check.setStatus("PASSED");
        } else if (anyFailed) {
            check.setStatus("FAILED");
        } else {
            check.setStatus("PARTIAL");
        }
        
        check.setNotes(request.getNotes());
        
        check = qualityCheckRepository.save(check);
        
        return QualityCheckResultDTO.builder()
            .itemId(itemId)
            .checklistId(check.getChecklistId())
            .status(check.getStatus())
            .checkResults(check.getResults().stream()
                .map(r -> QualityCheckResultDTO.CheckResultItemDTO.builder()
                    .item(r.getChecklistItemId().toString())
                    .passed(r.getPassed())
                    .notes(r.getNotes())
                    .build())
                .collect(Collectors.toList()))
            .defects(check.getDefects().stream()
                .map(this::mapToDefectDTO)
                .collect(Collectors.toList()))
            .checkedBy(getUserName(check.getCheckedBy()))
            .checkedAt(check.getCheckedAt())
            .nextAction(determineNextAction(check.getStatus()))
            .build();
    }
    
    @Transactional
    public DefectDTO reportDefect(UUID businessId, UUID orderId, UUID itemId, ReportDefectRequest request) {
        QualityCheck check = new QualityCheck();
        check.setOrderItemId(itemId);
        check.setCheckedBy(getCurrentUserId());
        check.setCheckedAt(LocalDateTime.now());
        check.setStatus("FAILED");
        check = qualityCheckRepository.save(check);
        
        Defect defect = new Defect();
        defect.setQualityCheck(check);
        defect.setType(request.getType());
        defect.setSeverity(request.getSeverity());
        defect.setDescription(request.getDescription());
        defect.setLocation(request.getLocation());
        defect.setImages(request.getImages() != null ? request.getImages() : new ArrayList<>());
        defect.setReportedBy(getCurrentUserId());
        defect.setReportedAt(LocalDateTime.now());
        defect.setStatus("OPEN");
        
        defect = defectRepository.save(defect);
        
        return mapToDefectDTO(defect);
    }
    
    @Transactional
    public DefectDTO updateDefect(UUID defectId, UpdateDefectRequest request) {
        Defect defect = defectRepository.findById(defectId)
            .orElseThrow(() -> new BusinessException("Defect not found", "DEFECT_NOT_FOUND"));
        
        if (request.getStatus() != null) {
            defect.setStatus(request.getStatus());
        }
        
        if (request.getResolution() != null) {
            defect.setResolution(request.getResolution());
        }
        
        if (request.getCompensation() != null) {
            defect.setCompensation(BigDecimal.valueOf(request.getCompensation()));
        }
        
        if (request.getCompensationType() != null) {
            defect.setCompensationType(request.getCompensationType());
        }
        
        if (request.getNotes() != null) {
            defect.setResolution(defect.getResolution() + "\nNotes: " + request.getNotes());
        }
        
        defect = defectRepository.save(defect);
        
        return mapToDefectDTO(defect);
    }
    
    @Transactional(readOnly = true)
    public PageResponse<DefectDTO> listDefects(UUID businessId, DefectFilter filter, Pageable pageable) {
        Page<Defect> page = defectRepository.findByFilters(
            businessId,
            filter != null ? filter.getStatus() : null,
            filter != null ? filter.getSeverity() : null,
            filter != null ? filter.getFromDate() : null,
            filter != null ? filter.getToDate() : null,
            filter != null ? filter.getAssignedTo() : null,
            pageable
        );
        return PageResponse.from(page.map(this::mapToDefectDTO));
    }
    
    @Transactional(readOnly = true)
    public List<EmployeeScorecardDTO> getEmployeeScorecards(UUID businessId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        
        List<Object[]> results = qualityCheckRepository.getEmployeePerformance(businessId, start, end);
        
        List<EmployeeScorecardDTO> scorecards = new ArrayList<>();
        
        for (Object[] result : results) {
            UUID employeeId = (UUID) result[0];
            String employeeName = (String) result[1];
            Long itemsProcessed = (Long) result[2];
            Long itemsPassed = (Long) result[3];
            Long itemsFailed = (Long) result[4];
            
            double passRate = itemsProcessed > 0 ? 
                (itemsPassed * 100.0 / itemsProcessed) : 0;
            double reworkRate = itemsProcessed > 0 ? 
                (itemsFailed * 100.0 / itemsProcessed) : 0;
            
            List<Defect> employeeDefects = defectRepository.findByReportedByAndReportedAtBetween(
                employeeId, start, end);
            
            Map<String, Long> defectsByType = employeeDefects.stream()
                .collect(Collectors.groupingBy(Defect::getType, Collectors.counting()));
            
            Map<String, Long> severityBreakdown = employeeDefects.stream()
                .collect(Collectors.groupingBy(Defect::getSeverity, Collectors.counting()));
            
            List<EmployeeScorecardDTO.TrendDTO> trend = qualityCheckRepository.getDailyPerformance(
                employeeId, start, end).stream()
                .map(t -> EmployeeScorecardDTO.TrendDTO.builder()
                    .date((LocalDate) t[0])
                    .passRate((Double) t[1])
                    .build())
                .collect(Collectors.toList());
            
            long rank = getEmployeeRank(businessId, employeeId, start, end);
            
            scorecards.add(EmployeeScorecardDTO.builder()
                .employeeId(employeeId)
                .employeeName(employeeName)
                .period(EmployeeScorecardDTO.PeriodDTO.builder()
                    .start(startDate)
                    .end(endDate)
                    .build())
                .itemsProcessed(itemsProcessed.intValue())
                .itemsPassed(itemsPassed.intValue())
                .itemsFailed(itemsFailed.intValue())
                .passRate(passRate)
                .reworkRate(reworkRate)
                .defectsByType(defectsByType.entrySet().stream()
                    .map(e -> EmployeeScorecardDTO.DefectTypeCountDTO.builder()
                        .type(e.getKey())
                        .count(e.getValue().intValue())
                        .build())
                    .collect(Collectors.toList()))
                .severityBreakdown(severityBreakdown)
                .trend(trend)
                .rank((int) rank)
                .build());
        }
        
        return scorecards;
    }
    
    @Transactional(readOnly = true)
    public EmployeeScorecardDTO getEmployeeScorecard(UUID businessId, UUID employeeId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        
        Long itemsProcessed = qualityCheckRepository.countItemsProcessedByEmployee(employeeId, start, end);
        Long itemsPassed = qualityCheckRepository.countItemsPassedByEmployee(employeeId, start, end);
        Long itemsFailed = qualityCheckRepository.countItemsFailedByEmployee(employeeId, start, end);
        
        if (itemsProcessed == null) itemsProcessed = 0L;
        if (itemsPassed == null) itemsPassed = 0L;
        if (itemsFailed == null) itemsFailed = 0L;
        
        double passRate = itemsProcessed > 0 ? 
            (itemsPassed * 100.0 / itemsProcessed) : 0;
        double reworkRate = itemsProcessed > 0 ? 
            (itemsFailed * 100.0 / itemsProcessed) : 0;
        
        List<Defect> employeeDefects = defectRepository.findByReportedByAndReportedAtBetween(
            employeeId, start, end);
        
        Map<String, Long> defectsByType = employeeDefects.stream()
            .collect(Collectors.groupingBy(Defect::getType, Collectors.counting()));
        
        Map<String, Long> severityBreakdown = employeeDefects.stream()
            .collect(Collectors.groupingBy(Defect::getSeverity, Collectors.counting()));
        
        List<EmployeeScorecardDTO.TrendDTO> trend = qualityCheckRepository.getDailyPerformance(
            employeeId, start, end).stream()
            .map(t -> EmployeeScorecardDTO.TrendDTO.builder()
                .date((LocalDate) t[0])
                .passRate((Double) t[1])
                .build())
            .collect(Collectors.toList());
        
        long rank = getEmployeeRank(businessId, employeeId, start, end);
        
        return EmployeeScorecardDTO.builder()
            .employeeId(employeeId)
            .employeeName(getUserName(employeeId))
            .period(EmployeeScorecardDTO.PeriodDTO.builder()
                .start(startDate)
                .end(endDate)
                .build())
            .itemsProcessed(itemsProcessed.intValue())
            .itemsPassed(itemsPassed.intValue())
            .itemsFailed(itemsFailed.intValue())
            .passRate(passRate)
            .reworkRate(reworkRate)
            .defectsByType(defectsByType.entrySet().stream()
                .map(e -> EmployeeScorecardDTO.DefectTypeCountDTO.builder()
                    .type(e.getKey())
                    .count(e.getValue().intValue())
                    .build())
                .collect(Collectors.toList()))
            .severityBreakdown(severityBreakdown)
            .trend(trend)
            .rank((int) rank)
            .build();
    }
    
    public Map<String, Long> getDefectTypeDistribution(UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
    List<Object[]> results = defectRepository.getDefectTypeDistribution(businessId, startDate, endDate);
    return results.stream()
        .collect(Collectors.toMap(
            r -> (String) r[0],
            r -> (Long) r[1]
        ));
}

public Map<String, Long> getSeverityDistribution(UUID businessId, LocalDateTime startDate, LocalDateTime endDate) {
    List<Object[]> results = defectRepository.getSeverityDistribution(businessId, startDate, endDate);
    return results.stream()
        .collect(Collectors.toMap(
            r -> (String) r[0],
            r -> (Long) r[1]
        ));
}
    
    private QualityChecklistDTO mapToChecklistDTO(QualityChecklist checklist) {
        return QualityChecklistDTO.builder()
            .id(checklist.getId())
            .name(checklist.getName())
            .serviceTypeId(checklist.getServiceTypeId())
            .garmentTypeId(checklist.getGarmentTypeId())
            .items(checklist.getItems().stream()
                .map(item -> QualityChecklistDTO.ItemDTO.builder()
                    .id(item.getId())
                    .description(item.getDescription())
                    .required(item.getRequired())
                    .order(item.getItemOrder())
                    .failureSeverity(item.getFailureSeverity())
                    .build())
                .collect(Collectors.toList()))
            .isActive(checklist.getIsActive())
            .createdAt(checklist.getCreatedAt())
            .build();
    }
    
    private DefectDTO mapToDefectDTO(Defect defect) {
        return DefectDTO.builder()
            .id(defect.getId())
            .type(defect.getType())
            .severity(defect.getSeverity())
            .description(defect.getDescription())
            .images(defect.getImages())
            .reportedBy(getUserName(defect.getReportedBy()))
            .reportedAt(defect.getReportedAt())
            .status(defect.getStatus())
            .resolution(defect.getResolution())
            .compensation(defect.getCompensation() != null ? defect.getCompensation().doubleValue() : null)
            .compensationType(defect.getCompensationType())
            .build();
    }
    
    private String determineNextAction(String status) {
        return switch (status) {
            case "PASSED" -> "PROCEED";
            case "FAILED" -> "REWORK";
            case "PARTIAL" -> "REVIEW";
            default -> "PROCEED";
        };
    }
    
    private long getEmployeeRank(UUID businessId, UUID employeeId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> rankings = qualityCheckRepository.getEmployeeRankings(businessId, start, end);
        for (int i = 0; i < rankings.size(); i++) {
            if (rankings.get(i)[0].equals(employeeId)) {
                return i + 1;
            }
        }
        return rankings.size() + 1;
    }
}

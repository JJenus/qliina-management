package com.jjenus.qliina_management.employee.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.PageResponse;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.identity.repository.ShopRepository;
import com.jjenus.qliina_management.employee.dto.*;
import com.jjenus.qliina_management.employee.model.*;
import com.jjenus.qliina_management.employee.repository.*;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.quality.repository.QualityCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jjenus.qliina_management.identity.model.Shop;
import org.springframework.data.domain.PageImpl;
import java.util.Objects;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {
    
    private final EmployeeShiftRepository shiftRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final EmployeeScheduleRepository scheduleRepository;
    private final EmployeeTargetRepository targetRepository;
    private final EmployeePerformanceRepository performanceRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final QualityCheckRepository qualityCheckRepository;
    
    // ==================== Shift Management ====================
    
    @Transactional(readOnly = true)
    public PageResponse<ShiftDTO> listShifts(UUID businessId, ShiftFilter filter, Pageable pageable) {
        Page<EmployeeShift> page;
        
        if (filter.getShopId() != null) {
            page = shiftRepository.findByShopId(filter.getShopId(), pageable);
        } else if (filter.getEmployeeId() != null) {
            page = shiftRepository.findByEmployeeId(filter.getEmployeeId(), pageable);
        } else {
            // Would need custom repository method to filter by businessId
            page = Page.empty();
        }
        
        return PageResponse.from(page.map(this::mapToShiftDTO));
    }
    
    @Transactional
    public TimeEntryDTO clockIn(UUID businessId, UUID employeeId, ClockInRequest request) {
        // Check if already clocked in
        Optional<EmployeeShift> activeShift = shiftRepository.findActiveShift(employeeId);
        if (activeShift.isPresent()) {
            throw new BusinessException("Employee already has an active shift", "ACTIVE_SHIFT_EXISTS");
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        
        // Check if there's a scheduled shift for today
        Optional<EmployeeShift> scheduledShift = shiftRepository.findByEmployeeIdAndDate(employeeId, today);
        
        EmployeeShift shift;
        if (scheduledShift.isPresent()) {
            shift = scheduledShift.get();
            shift.setActualStart(now);
            shift.setStatus(EmployeeShift.ShiftStatus.CHECKED_IN);
        } else {
            // Create new shift
            shift = new EmployeeShift();
            shift.setBusinessId(businessId);
            shift.setEmployeeId(employeeId);
            shift.setShopId(request.getShopId());
            shift.setDate(today);
            shift.setScheduledStart(now);
            shift.setScheduledEnd(now.plusHours(8)); // Default 8-hour shift
            shift.setActualStart(now);
            shift.setStatus(EmployeeShift.ShiftStatus.CHECKED_IN);
        }
        
        shift = shiftRepository.save(shift);
        
        // Create time entry
        TimeEntry entry = createTimeEntry(businessId, employeeId, request.getShopId(), 
            TimeEntry.EventType.CLOCK_IN, shift.getId(), request.getDeviceInfo(), request.getNotes());
        
        return mapToTimeEntryDTO(entry);
    }
    
    @Transactional
    public TimeEntryDTO clockOut(UUID businessId, UUID employeeId, ClockOutRequest request) {
        EmployeeShift shift;
        
        if (request.getShiftId() != null) {
            shift = shiftRepository.findById(request.getShiftId())
                .orElseThrow(() -> new BusinessException("Shift not found", "SHIFT_NOT_FOUND"));
        } else {
            shift = shiftRepository.findActiveShift(employeeId)
                .orElseThrow(() -> new BusinessException("No active shift found", "NO_ACTIVE_SHIFT"));
        }
        
        if (!shift.getEmployeeId().equals(employeeId)) {
            throw new BusinessException("Unauthorized to clock out this shift", "UNAUTHORIZED");
        }
        
        LocalDateTime now = LocalDateTime.now();
        shift.setActualEnd(now);
        shift.setStatus(EmployeeShift.ShiftStatus.CHECKED_OUT);
        shift.calculateWorkMinutes();
        shift.setNotes(request.getNotes());
        
        shift = shiftRepository.save(shift);
        
        // Create time entry
        TimeEntry entry = createTimeEntry(businessId, employeeId, shift.getShopId(), 
            TimeEntry.EventType.CLOCK_OUT, shift.getId(), null, request.getNotes());
        
        // Update attendance record
        updateAttendance(employeeId, shift.getDate());
        
        return mapToTimeEntryDTO(entry);
    }
    
    @Transactional
    public TimeEntryDTO startBreak(UUID businessId, UUID employeeId) {
        EmployeeShift shift = shiftRepository.findActiveShift(employeeId)
            .orElseThrow(() -> new BusinessException("No active shift found", "NO_ACTIVE_SHIFT"));
        
        shift.startBreak();
        shift = shiftRepository.save(shift);
        
        TimeEntry entry = createTimeEntry(businessId, employeeId, shift.getShopId(), 
            TimeEntry.EventType.BREAK_START, shift.getId(), null, null);
        
        return mapToTimeEntryDTO(entry);
    }
    
    @Transactional
    public TimeEntryDTO endBreak(UUID businessId, UUID employeeId) {
        EmployeeShift shift = shiftRepository.findActiveShift(employeeId)
            .orElseThrow(() -> new BusinessException("No active shift found", "NO_ACTIVE_SHIFT"));
        
        if (shift.getStatus() != EmployeeShift.ShiftStatus.ON_BREAK) {
            throw new BusinessException("Employee is not on break", "NOT_ON_BREAK");
        }
        
        shift.endBreak();
        shift = shiftRepository.save(shift);
        
        TimeEntry entry = createTimeEntry(businessId, employeeId, shift.getShopId(), 
            TimeEntry.EventType.BREAK_END, shift.getId(), null, null);
        
        return mapToTimeEntryDTO(entry);
    }
    
    @Transactional(readOnly = true)
    public TimesheetDTO getTimesheet(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        User employee = userRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessException("Employee not found", "EMPLOYEE_NOT_FOUND"));
        
        List<EmployeeShift> shifts = shiftRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate);
        
        List<ShiftDTO> shiftDTOs = shifts.stream()
            .map(this::mapToShiftDTO)
            .collect(Collectors.toList());
        
        int totalScheduledMinutes = shifts.stream()
            .mapToInt(s -> (int) ChronoUnit.MINUTES.between(s.getScheduledStart(), s.getScheduledEnd()))
            .sum();
        
        int totalWorkedMinutes = shifts.stream()
            .filter(s -> s.getTotalWorkMinutes() != null)
            .mapToInt(EmployeeShift::getTotalWorkMinutes)
            .sum();
        
        int totalBreakMinutes = shifts.stream()
            .mapToInt(EmployeeShift::getTotalBreakMinutes)
            .sum();
        
        int totalOvertimeMinutes = shifts.stream()
            .mapToInt(EmployeeShift::getOvertimeMinutes)
            .sum();
        
        // Calculate pay (example rates)
        double hourlyRate = 15.0;
        double overtimeRate = hourlyRate * 1.5;
        
        BigDecimal regularPay = BigDecimal.valueOf(totalWorkedMinutes - totalOvertimeMinutes)
            .divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(hourlyRate));
        
        BigDecimal overtimePay = BigDecimal.valueOf(totalOvertimeMinutes)
            .divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(overtimeRate));
        
        TimesheetDTO.SummaryDTO summary = TimesheetDTO.SummaryDTO.builder()
            .totalScheduledMinutes(totalScheduledMinutes)
            .totalWorkedMinutes(totalWorkedMinutes)
            .totalBreakMinutes(totalBreakMinutes)
            .totalOvertimeMinutes(totalOvertimeMinutes)
            .regularPay(regularPay)
            .overtimePay(overtimePay)
            .totalPay(regularPay.add(overtimePay))
            .hourlyRate(hourlyRate)
            .build();
        
        return TimesheetDTO.builder()
            .employeeId(employeeId)
            .employeeName(employee.getFirstName() + " " + employee.getLastName())
            .period(TimesheetDTO.PeriodDTO.builder()
                .start(startDate)
                .end(endDate)
                .build())
            .entries(shiftDTOs)
            .summary(summary)
            .build();
    }
    
    // ==================== Schedule Management ====================
    
    @Transactional
    public ScheduleDTO createSchedule(UUID businessId, CreateScheduleRequest request) {
        EmployeeSchedule schedule = new EmployeeSchedule();
        schedule.setBusinessId(businessId);
        schedule.setEmployeeId(request.getEmployeeId());
        schedule.setShopId(request.getShopId());
        schedule.setDate(request.getDate());
        if (request.getDate() != null) {
            schedule.setDayOfWeek(request.getDate().getDayOfWeek());
        }
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setRole(request.getRole());
        schedule.setIsRecurring(request.getIsRecurring() != null ? request.getIsRecurring() : false);
        schedule.setRecurringPattern(request.getRecurringPattern());
        schedule.setNotes(request.getNotes());
        
        schedule = scheduleRepository.save(schedule);
        
        // Generate shifts from recurring schedule
        if (schedule.getIsRecurring()) {
            generateShiftsFromSchedule(schedule);
        }
        
        return mapToScheduleDTO(schedule);
    }
    
    @Transactional(readOnly = true)
    public List<ScheduleDTO> getSchedule(UUID businessId, UUID shopId, LocalDate startDate, LocalDate endDate) {
        List<EmployeeSchedule> schedules;
        
        if (shopId != null) {
            schedules = scheduleRepository.findByShopIdAndDateRange(shopId, startDate, endDate);
        } else {
            // Would need business-level query
            schedules = new ArrayList<>();
        }
        
        // Add recurring schedules
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<EmployeeSchedule> recurring = scheduleRepository.findRecurringSchedulesByDay(shopId, date.getDayOfWeek());
            schedules.addAll(recurring);
        }
        
        return schedules.stream()
            .map(this::mapToScheduleDTO)
            .distinct()
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public AttendanceReportDTO getAttendanceReport(UUID businessId, UUID shopId, LocalDate startDate, LocalDate endDate) {
        List<Attendance> attendances;
        String shopName = "";
        
        if (shopId != null) {
            attendances = attendanceRepository.findByShopIdAndDateRange(shopId, startDate, endDate);
            shopName = shopRepository.findById(shopId).map(s -> s.getName()).orElse("");
        } else {
            // Would need business-level query
            attendances = new ArrayList<>();
        }
        
        long totalEmployees = userRepository.countByBusinessIdAndEnabledTrue(businessId);
        
        // Calculate summary
        long totalAbsences = attendances.stream()
            .filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT)
            .count();
        
        long totalLates = attendances.stream()
            .filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE)
            .count();
        
        Map<String, Long> statusBreakdown = attendances.stream()
            .collect(Collectors.groupingBy(
                a -> a.getStatus().toString(),
                Collectors.counting()
            ));
        
        double averageOvertime = attendances.stream()
            .filter(a -> a.getOvertimeHours() != null)
            .mapToDouble(Attendance::getOvertimeHours)
            .average()
            .orElse(0.0);
        
        // Daily breakdown
        List<AttendanceReportDTO.DailyAttendanceDTO> dailyBreakdown = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            List<Attendance> dayAttendances = attendances.stream()
                .filter(a -> a.getDate().equals(currentDate))
                .collect(Collectors.toList());
            
            long present = dayAttendances.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT)
                .count();
            
            long absent = dayAttendances.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT)
                .count();
            
            long late = dayAttendances.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE)
                .count();
            
            long onLeave = dayAttendances.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.LEAVE || 
                            a.getStatus() == Attendance.AttendanceStatus.SICK)
                .count();
            
            double attendanceRate = totalEmployees > 0 ? 
                (present * 100.0 / totalEmployees) : 0.0;
            
            dailyBreakdown.add(AttendanceReportDTO.DailyAttendanceDTO.builder()
                .date(date)
                .present(present)
                .absent(absent)
                .late(late)
                .onLeave(onLeave)
                .attendanceRate(attendanceRate)
                .build());
        }
        
        // Employee breakdown
        Map<UUID, List<Attendance>> byEmployee = attendances.stream()
            .collect(Collectors.groupingBy(Attendance::getEmployeeId));
        
        List<AttendanceReportDTO.EmployeeAttendanceDTO> employeeBreakdown = new ArrayList<>();
        for (Map.Entry<UUID, List<Attendance>> entry : byEmployee.entrySet()) {
            UUID empId = entry.getKey();
            List<Attendance> empAttendances = entry.getValue();
            
            long workingDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            long present = empAttendances.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT)
                .count();
            
            long absent = empAttendances.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT)
                .count();
            
            long late = empAttendances.stream()
                .filter(a -> a.getStatus() == Attendance.AttendanceStatus.LATE)
                .count();
            
            double attendanceRate = workingDays > 0 ? (present * 100.0 / workingDays) : 0.0;
            
            double avgOvertime = empAttendances.stream()
                .filter(a -> a.getOvertimeHours() != null)
                .mapToDouble(Attendance::getOvertimeHours)
                .average()
                .orElse(0.0);
            
            String employeeName = userRepository.findById(empId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse("");
            
            employeeBreakdown.add(AttendanceReportDTO.EmployeeAttendanceDTO.builder()
                .employeeId(empId)
                .employeeName(employeeName)
                .presentDays((int) present)
                .absentDays((int) absent)
                .lateDays((int) late)
                .attendanceRate(attendanceRate)
                .averageOvertime(avgOvertime)
                .build());
        }
        
        AttendanceReportDTO.SummaryDTO summary = AttendanceReportDTO.SummaryDTO.builder()
            .totalEmployees(totalEmployees)
            .averageAttendance(totalEmployees > 0 ? 
                (attendances.size() * 100.0 / (totalEmployees * dailyBreakdown.size())) : 0.0)
            .totalAbsences(totalAbsences)
            .totalLates(totalLates)
            .averageOvertime(averageOvertime)
            .statusBreakdown(statusBreakdown)
            .build();
        
        return AttendanceReportDTO.builder()
            .shopId(shopId)
            .shopName(shopName)
            .period(AttendanceReportDTO.PeriodDTO.builder()
                .start(startDate)
                .end(endDate)
                .build())
            .summary(summary)
            .dailyBreakdown(dailyBreakdown)
            .employeeBreakdown(employeeBreakdown)
            .build();
    }
    
    // ==================== Targets Management ====================
    
    @Transactional
    public EmployeeTargetsDTO setTargets(UUID businessId, SetTargetsRequest request) {
        List<EmployeeTarget> savedTargets = new ArrayList<>();
        
        for (SetTargetsRequest.TargetDTO targetDto : request.getTargets()) {
            Optional<EmployeeTarget> existing = targetRepository.findByEmployeeIdAndDateAndMetric(
                request.getEmployeeId(), targetDto.getDate(), 
                EmployeeTarget.TargetMetric.valueOf(targetDto.getMetric()));
            
            EmployeeTarget target;
            if (existing.isPresent()) {
                target = existing.get();
                target.setTargetValue(targetDto.getTarget());
            } else {
                target = new EmployeeTarget();
                target.setBusinessId(businessId);
                target.setEmployeeId(request.getEmployeeId());
                target.setDate(targetDto.getDate());
                target.setMetric(EmployeeTarget.TargetMetric.valueOf(targetDto.getMetric()));
                target.setTargetValue(targetDto.getTarget());
            }
            
            savedTargets.add(targetRepository.save(target));
        }
        
        return getEmployeeTargets(request.getEmployeeId(), 
            savedTargets.stream().map(EmployeeTarget::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()),
            savedTargets.stream().map(EmployeeTarget::getDate).max(LocalDate::compareTo).orElse(LocalDate.now()));
    }
    
    @Transactional(readOnly = true)
    public EmployeeTargetsDTO getEmployeeTargets(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        List<EmployeeTarget> targets = targetRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate);
        
        String employeeName = userRepository.findById(employeeId)
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("");
        
        List<EmployeeTargetsDTO.TargetDTO> targetDTOs = targets.stream()
            .map(t -> {
                double achievementRate = t.getActualValue() != null ?
                    (t.getActualValue() * 100.0 / t.getTargetValue()) : 0.0;
                
                return EmployeeTargetsDTO.TargetDTO.builder()
                    .date(t.getDate())
                    .metric(t.getMetric().toString())
                    .target(t.getTargetValue())
                    .actual(t.getActualValue())
                    .achieved(t.getAchieved())
                    .achievementRate(achievementRate)
                    .build();
            })
            .collect(Collectors.toList());
        
        long totalTargets = targets.size();
        long achievedTargets = targets.stream()
            .filter(t -> Boolean.TRUE.equals(t.getAchieved()))
            .count();
        
        double overallAchievement = totalTargets > 0 ? 
            (achievedTargets * 100.0 / totalTargets) : 0.0;
        
        Map<EmployeeTarget.TargetMetric, List<EmployeeTarget>> byMetric = targets.stream()
            .collect(Collectors.groupingBy(EmployeeTarget::getMetric));
        
        List<EmployeeTargetsDTO.MetricAchievementDTO> byMetricList = byMetric.entrySet().stream()
            .map(e -> {
                long metricTotal = e.getValue().size();
                long metricAchieved = e.getValue().stream()
                    .filter(t -> Boolean.TRUE.equals(t.getAchieved()))
                    .count();
                double rate = metricTotal > 0 ? (metricAchieved * 100.0 / metricTotal) : 0.0;
                
                return EmployeeTargetsDTO.MetricAchievementDTO.builder()
                    .metric(e.getKey().toString())
                    .achievementRate(rate)
                    .achieved((int) metricAchieved)
                    .total((int) metricTotal)
                    .build();
            })
            .collect(Collectors.toList());
        
        EmployeeTargetsDTO.AchievementSummaryDTO summary = EmployeeTargetsDTO.AchievementSummaryDTO.builder()
            .overallAchievement(overallAchievement)
            .targetsAchieved((int) achievedTargets)
            .totalTargets((int) totalTargets)
            .byMetric(byMetricList)
            .build();
        
        return EmployeeTargetsDTO.builder()
            .employeeId(employeeId)
            .employeeName(employeeName)
            .targets(targetDTOs)
            .summary(summary)
            .build();
    }
    
    // ==================== Performance Management ====================
    
    @Transactional(readOnly = true)
    public EmployeePerformanceDTO getEmployeePerformance(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        User employee = userRepository.findById(employeeId)
            .orElseThrow(() -> new BusinessException("Employee not found", "EMPLOYEE_NOT_FOUND"));
        
        // Calculate metrics
        Integer ordersProcessed = orderRepository.countByEmployeeIdAndDateRange(employeeId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        Integer itemsProcessed = orderRepository.countItemsByEmployeeIdAndDateRange(employeeId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        BigDecimal revenueHandled = orderRepository.sumRevenueByEmployeeIdAndDateRange(employeeId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        Double qualityScore = qualityCheckRepository.averageScoreByEmployeeIdAndDateRange(employeeId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        // Attendance metrics
        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate);
        long workingDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        long presentDays = attendances.stream()
            .filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT)
            .count();
        double attendanceRate = workingDays > 0 ? (presentDays * 100.0 / workingDays) : 0.0;
        
        // On-time rate
        long onTimeDays = attendances.stream()
            .filter(a -> a.getStatus() != Attendance.AttendanceStatus.LATE)
            .count();
        double ontimeRate = workingDays > 0 ? (onTimeDays * 100.0 / workingDays) : 0.0;
        
        // Rework rate from quality checks
        Double reworkRate = qualityCheckRepository.reworkRateByEmployeeIdAndDateRange(employeeId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        // Target achievement
        Double targetAchievement = targetRepository.averageAchievementRate(employeeId, startDate, endDate);
        
        // Daily breakdown
        List<EmployeePerformanceDTO.DailyMetricDTO> dailyMetrics = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDate currentDate = date;
            Integer dailyOrders = orderRepository.countByEmployeeIdAndDate(employeeId, currentDate);
            Integer dailyItems = orderRepository.countItemsByEmployeeIdAndDate(employeeId, currentDate);
            Double dailyQuality = qualityCheckRepository.averageScoreByEmployeeIdAndDate(employeeId, currentDate);
            
            dailyMetrics.add(EmployeePerformanceDTO.DailyMetricDTO.builder()
                .date(date)
                .orders(dailyOrders != null ? dailyOrders : 0)
                .items(dailyItems != null ? dailyItems : 0)
                .quality(dailyQuality != null ? dailyQuality : 0.0)
                .build());
        }
        
        // Get rank
        List<Object[]> rankings = performanceRepository.getEmployeeRankings(
            employee.getBusinessId(), startDate, endDate, Pageable.unpaged());
        int rank = 1;
        for (Object[] ranking : rankings) {
            if (ranking[0].equals(employeeId)) {
                break;
            }
            rank++;
        }
        
        EmployeePerformanceDTO.MetricsDTO metrics = EmployeePerformanceDTO.MetricsDTO.builder()
            .ordersProcessed(ordersProcessed != null ? ordersProcessed : 0)
            .itemsProcessed(itemsProcessed != null ? itemsProcessed : 0)
            .revenueHandled(revenueHandled != null ? revenueHandled : BigDecimal.ZERO)
            .qualityScore(qualityScore != null ? qualityScore : 0.0)
            .attendanceRate(attendanceRate)
            .ontimeRate(ontimeRate)
            .reworkRate(reworkRate != null ? reworkRate : 0.0)
            .customerSatisfaction(0.0) // Would need customer feedback data
            .targetAchievement(targetAchievement != null ? targetAchievement : 0.0)
            .build();
        
        return EmployeePerformanceDTO.builder()
            .employeeId(employeeId)
            .employeeName(employee.getFirstName() + " " + employee.getLastName())
            .role(employee.getRoles().stream().findFirst().map(r -> r.getRole().getName()).orElse(""))
            .period(EmployeePerformanceDTO.PeriodDTO.builder()
                .start(startDate)
                .end(endDate)
                .build())
            .metrics(metrics)
            .byDay(dailyMetrics)
            .rank(rank)
            .build();
    }
    
    // ==================== New Service Methods (All fields verified to exist) ====================

/**
 * Get current active shift for an employee
 * Uses: EmployeeShift entity with fields: employeeId, status, actualStart, actualEnd, etc.
 */
@Transactional(readOnly = true)
public ShiftDTO getCurrentShift(UUID employeeId) {
    EmployeeShift shift = shiftRepository.findActiveShift(employeeId)
        .orElseThrow(() -> new BusinessException("No active shift found", "NO_ACTIVE_SHIFT"));
    return mapToShiftDTO(shift);
}

/**
 * Get schedule for a specific employee
 * Uses: EmployeeSchedule entity with fields: employeeId, date, startTime, endTime, etc.
 */
@Transactional(readOnly = true)
public List<ScheduleDTO> getEmployeeSchedule(UUID employeeId, LocalDate startDate, LocalDate endDate) {
    List<EmployeeSchedule> schedules = scheduleRepository.findByEmployeeIdAndDateRange(employeeId, startDate, endDate);
    return schedules.stream()
        .map(this::mapToScheduleDTO)
        .collect(Collectors.toList());
}

/**
 * Update target achievement
 * Uses: EmployeeTarget entity with fields: id, actualValue, achieved, targetValue, etc.
 */
@Transactional
public EmployeeTargetsDTO.TargetDTO updateTargetAchievement(UUID targetId, Integer actualValue, String notes) {
    EmployeeTarget target = targetRepository.findById(targetId)
        .orElseThrow(() -> new BusinessException("Target not found", "TARGET_NOT_FOUND"));
    
    target.setActualValue(actualValue);
    if (notes != null) {
        target.setNotes(notes);
    }
    target.updateAchieved();
    target = targetRepository.save(target);
    
    double achievementRate = target.getActualValue() != null ?
        (target.getActualValue() * 100.0 / target.getTargetValue()) : 0.0;
    
    return EmployeeTargetsDTO.TargetDTO.builder()
        .date(target.getDate())
        .metric(target.getMetric().toString())
        .target(target.getTargetValue())
        .actual(target.getActualValue())
        .achieved(target.getAchieved())
        .achievementRate(achievementRate)
        .build();
}

/**
 * Get performance leaderboard
 * Uses: User entity fields: id, firstName, lastName, businessId
 * Uses: Shop entity for name lookup
 * Uses: EmployeePerformanceDTO from existing getEmployeePerformance method
 */
@Transactional(readOnly = true)
public List<EmployeePerformanceDTO> getPerformanceLeaderboard(UUID businessId, UUID shopId, String role, 
                                                              LocalDate startDate, LocalDate endDate) {
    // Get all employees
    List<User> employees;
    if (shopId != null) {
        Page<User> page = userRepository.findByShopId(shopId, Pageable.unpaged());
        employees = page.getContent();
    } else {
        Page<User> page = userRepository.findByBusinessId(businessId, Pageable.unpaged());
        employees = page.getContent();
    }
    
    // Generate performance for each employee using existing method
    List<EmployeePerformanceDTO> performances = employees.stream()
        .map(e -> {
            try {
                return getEmployeePerformance(e.getId(), startDate, endDate);
            } catch (Exception ex) {
                log.error("Error generating performance for employee: {}", e.getId(), ex);
                return null;
            }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    
    // Sort by calculated score
    performances.sort((a, b) -> {
        double aScore = calculatePerformanceScore(a);
        double bScore = calculatePerformanceScore(b);
        return Double.compare(bScore, aScore);
    });
    
    // Assign ranks
    for (int i = 0; i < performances.size(); i++) {
        performances.get(i).setRank(i + 1);
    }
    
    return performances;
}

/**
 * Get employee details
 * Uses: User entity fields: id, firstName, lastName, email, phone, enabled, primaryShopId
 * Uses: Shop entity for shop name lookup
 * Uses: EmployeeShift for current shift status
 */
@Transactional(readOnly = true)
public EmployeeDetailDTO getEmployee(UUID employeeId) {
    User user = userRepository.findById(employeeId)
        .orElseThrow(() -> new BusinessException("Employee not found", "EMPLOYEE_NOT_FOUND"));
    
    Optional<EmployeeShift> currentShift = shiftRepository.findActiveShift(employeeId);
    String shopName = null;
    if (user.getPrimaryShopId() != null) {
        shopName = shopRepository.findById(user.getPrimaryShopId())
            .map(Shop::getName)
            .orElse(null);
    }
    
    String role = user.getRoles().stream()
        .findFirst()
        .map(userRole -> userRole.getRole().getName())
        .orElse("");
    
    return EmployeeDetailDTO.builder()
        .id(user.getId())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .email(user.getEmail())
        .phone(user.getPhone())
        .role(role)
        .shopId(user.getPrimaryShopId())
        .shopName(shopName)
        .employmentStatus(user.getEnabled() ? "ACTIVE" : "INACTIVE")
        .lastClockIn(currentShift.map(EmployeeShift::getActualStart).orElse(null))
        .isClockedIn(currentShift.isPresent())
        .build();
}

/**
 * List employees with filters
 * Uses: UserRepository existing methods: findByShopId, searchUsers, findByBusinessId
 */
@Transactional(readOnly = true)
public PageResponse<EmployeeSummaryDTO> listEmployees(UUID businessId, UUID shopId, String role, 
                                                       String search, Pageable pageable) {
    Page<User> page;
    
    if (shopId != null) {
        page = userRepository.findByShopId(shopId, pageable);
    } else if (search != null && !search.trim().isEmpty()) {
        page = userRepository.searchUsers(businessId, search, pageable);
    } else {
        page = userRepository.findByBusinessId(businessId, pageable);
    }
    
    List<EmployeeSummaryDTO> dtos = page.getContent().stream()
        .map(this::mapToEmployeeSummary)
        .collect(Collectors.toList());
    
    return PageResponse.from(new PageImpl<>(dtos, pageable, page.getTotalElements()));
}

/**
 * Helper: Map User to EmployeeSummaryDTO
 */
private EmployeeSummaryDTO mapToEmployeeSummary(User user) {
    String shopName = null;
    if (user.getPrimaryShopId() != null) {
        shopName = shopRepository.findById(user.getPrimaryShopId())
            .map(Shop::getName)
            .orElse(null);
    }
    
    String role = user.getRoles().stream()
        .findFirst()
        .map(userRole -> userRole.getRole().getName())
        .orElse("");
    
    return EmployeeSummaryDTO.builder()
        .id(user.getId())
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .email(user.getEmail())
        .phone(user.getPhone())
        .role(role)
        .shopName(shopName)
        .isActive(user.getEnabled())
        .build();
}

/**
 * Helper: Calculate performance score for leaderboard
 */
private double calculatePerformanceScore(EmployeePerformanceDTO perf) {
    if (perf == null || perf.getMetrics() == null) return 0.0;
    EmployeePerformanceDTO.MetricsDTO m = perf.getMetrics();
    
    double qualityScore = m.getQualityScore() != null ? m.getQualityScore() : 0.0;
    double productivity = m.getProductivity() != null ? m.getProductivity() : 0.0;
    double attendanceRate = m.getAttendanceRate() != null ? m.getAttendanceRate() : 0.0;
    double targetAchievement = m.getTargetAchievement() != null ? m.getTargetAchievement() : 0.0;
    
    return (qualityScore * 0.3) +
           (productivity * 0.3) +
           (attendanceRate * 0.2) +
           (targetAchievement * 0.2);
}
    
    // ==================== Helper Methods ====================
    
    private TimeEntry createTimeEntry(UUID businessId, UUID employeeId, UUID shopId, 
                                        TimeEntry.EventType eventType, UUID shiftId,
                                        ClockInRequest.DeviceInfo deviceInfo, String notes) {
        TimeEntry entry = new TimeEntry();
        entry.setBusinessId(businessId);
        entry.setEmployeeId(employeeId);
        entry.setShopId(shopId);
        entry.setEventType(eventType);
        entry.setTimestamp(LocalDateTime.now());
        entry.setShiftId(shiftId);
        
        if (deviceInfo != null) {
            entry.setDeviceId(deviceInfo.getDeviceId());
            entry.setLocation(deviceInfo.getLocation());
        }
        
        entry.setNotes(notes);
        
        return timeEntryRepository.save(entry);
    }
    
    private void updateAttendance(UUID employeeId, LocalDate date) {
        EmployeeShift shift = shiftRepository.findByEmployeeIdAndDate(employeeId, date)
            .orElse(null);
        
        if (shift == null) return;
        
        Attendance attendance = attendanceRepository.findByEmployeeIdAndDate(employeeId, date)
            .orElse(new Attendance());
        
        attendance.setBusinessId(shift.getBusinessId());
        attendance.setEmployeeId(employeeId);
        attendance.setDate(date);
        attendance.setCheckInTime(shift.getActualStart());
        attendance.setCheckOutTime(shift.getActualEnd());
        
        if (shift.getTotalWorkMinutes() != null) {
            attendance.setTotalHours(shift.getTotalWorkMinutes() / 60.0);
        }
        
        if (shift.getOvertimeMinutes() != null) {
            attendance.setOvertimeHours(shift.getOvertimeMinutes() / 60.0);
        }
        
        // Determine status
        if (shift.getActualStart() == null) {
            attendance.setStatus(Attendance.AttendanceStatus.ABSENT);
        } else if (shift.getActualStart().isAfter(shift.getScheduledStart().plusMinutes(15))) {
            attendance.setStatus(Attendance.AttendanceStatus.LATE);
        } else {
            attendance.setStatus(Attendance.AttendanceStatus.PRESENT);
        }
        
        attendanceRepository.save(attendance);
    }
    
    private void generateShiftsFromSchedule(EmployeeSchedule schedule) {
        if (!schedule.getIsRecurring()) return;
        
        LocalDate startDate = schedule.getDate() != null ? schedule.getDate() : LocalDate.now();
        LocalDate endDate = startDate.plusMonths(1); // Generate for next month
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusWeeks(1)) {
            if (schedule.getDayOfWeek() != null && !date.getDayOfWeek().equals(schedule.getDayOfWeek())) {
                continue;
            }
            
            Optional<EmployeeShift> existing = shiftRepository.findByEmployeeIdAndDate(schedule.getEmployeeId(), date);
            if (!existing.isPresent()) {
                EmployeeShift shift = new EmployeeShift();
                shift.setBusinessId(schedule.getBusinessId());
                shift.setEmployeeId(schedule.getEmployeeId());
                shift.setShopId(schedule.getShopId());
                shift.setDate(date);
                shift.setScheduledStart(schedule.getScheduledStartForDate(date));
                shift.setScheduledEnd(schedule.getScheduledEndForDate(date));
                shift.setStatus(EmployeeShift.ShiftStatus.SCHEDULED);
                shift.setNotes(schedule.getNotes());
                
                shiftRepository.save(shift);
            }
        }
    }
    
    private ShiftDTO mapToShiftDTO(EmployeeShift shift) {
        String employeeName = userRepository.findById(shift.getEmployeeId())
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("");
        
        String shopName = shopRepository.findById(shift.getShopId())
            .map(s -> s.getName())
            .orElse("");
        
        return ShiftDTO.builder()
            .id(shift.getId())
            .employeeId(shift.getEmployeeId())
            .employeeName(employeeName)
            .shopId(shift.getShopId())
            .shopName(shopName)
            .date(shift.getDate())
            .scheduledStart(shift.getScheduledStart())
            .scheduledEnd(shift.getScheduledEnd())
            .actualStart(shift.getActualStart())
            .actualEnd(shift.getActualEnd())
            .breakStart(shift.getBreakStart())
            .breakEnd(shift.getBreakEnd())
            .totalBreakMinutes(shift.getTotalBreakMinutes())
            .totalWorkMinutes(shift.getTotalWorkMinutes())
            .overtimeMinutes(shift.getOvertimeMinutes())
            .status(shift.getStatus() != null ? shift.getStatus().toString() : null)
            .notes(shift.getNotes())
            .build();
    }
    
    private TimeEntryDTO mapToTimeEntryDTO(TimeEntry entry) {
        String employeeName = userRepository.findById(entry.getEmployeeId())
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("");
        
        String shopName = shopRepository.findById(entry.getShopId())
            .map(s -> s.getName())
            .orElse("");
        
        TimeEntryDTO.DeviceInfoDTO deviceInfo = TimeEntryDTO.DeviceInfoDTO.builder()
            .deviceId(entry.getDeviceId())
            .ipAddress(entry.getIpAddress())
            .location(entry.getLocation())
            .build();
        
        return TimeEntryDTO.builder()
            .id(entry.getId())
            .employeeId(entry.getEmployeeId())
            .employeeName(employeeName)
            .shopId(entry.getShopId())
            .shopName(shopName)
            .eventType(entry.getEventType().toString())
            .timestamp(entry.getTimestamp())
            .deviceInfo(deviceInfo)
            .notes(entry.getNotes())
            .build();
    }
    
    private ScheduleDTO mapToScheduleDTO(EmployeeSchedule schedule) {
        String employeeName = userRepository.findById(schedule.getEmployeeId())
            .map(u -> u.getFirstName() + " " + u.getLastName())
            .orElse("");
        
        String shopName = shopRepository.findById(schedule.getShopId())
            .map(s -> s.getName())
            .orElse("");
        
        return ScheduleDTO.builder()
            .id(schedule.getId())
            .employeeId(schedule.getEmployeeId())
            .employeeName(employeeName)
            .shopId(schedule.getShopId())
            .shopName(shopName)
            .date(schedule.getDate())
            .dayOfWeek(schedule.getDayOfWeek() != null ? schedule.getDayOfWeek().toString() : null)
            .startTime(schedule.getStartTime())
            .endTime(schedule.getEndTime())
            .role(schedule.getRole())
            .isRecurring(schedule.getIsRecurring())
            .recurringPattern(schedule.getRecurringPattern())
            .notes(schedule.getNotes())
            .build();
    }
}

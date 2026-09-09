package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.employee.model.EmployeePerformance;
import com.jjenus.qliina_management.employee.model.EmployeeShift;
import com.jjenus.qliina_management.employee.repository.EmployeePerformanceRepository;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.employee.service.ShiftGateService;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.repository.OrderRepository;
import com.jjenus.qliina_management.quality.repository.QualityCheckRepository;
import com.jjenus.qliina_management.reporting.dto.EmployeePerfDTO;
import com.jjenus.qliina_management.reporting.dto.EmployeePerfRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeReportService {
    
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final QualityCheckRepository qualityRepository;
    private final EmployeeShiftRepository shiftRepository;
    private final EmployeePerformanceRepository performanceRepository;
    private final ShiftGateService shiftGate;
    
    public List<EmployeePerfDTO> generateEmployeePerformanceReport(UUID businessId, EmployeePerfRequest request) {
        LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = request.getEndDate().atTime(23, 59, 59);
        
        List<User> employees;
        if (request.getEmployeeId() != null) {
            employees = Collections.singletonList(
                userRepository.findById(request.getEmployeeId())
                    .orElseThrow(() -> new BusinessException("Employee not found", "EMPLOYEE_NOT_FOUND"))
            );
        } else {
           List<String> roles = Arrays.asList("WASHER", "IRONER", "DELIVERY", "FRONT_DESK");
          employees = userRepository.findByBusinessIdAndRoles(businessId, roles);
        }
        
        List<EmployeePerfDTO> results = new ArrayList<>();
        
        for (User employee : employees) {
            if (request.getRole() != null && !hasRole(employee, request.getRole())) {
                continue;
            }
            
            EmployeePerfDTO perf = generateEmployeePerformance(
                employee, request.getStartDate(), request.getEndDate(), businessId);
            results.add(perf);
        }
        
        // Sort by overall performance and assign ranks
        results.sort((a, b) -> {
            double aScore = calculatePerformanceScore(a);
            double bScore = calculatePerformanceScore(b);
            return Double.compare(bScore, aScore);
        });
        
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setRank(i + 1);
        }
        
        return results;
    }
    
    private EmployeePerfDTO generateEmployeePerformance(User employee, LocalDate startDate, LocalDate endDate, UUID businessId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        String primaryRole = getPrimaryRole(employee);

        Integer ordersProcessed = orderRepository.countByEmployeeIdAndDateRange(
            employee.getId(), startDateTime, endDateTime);

        Integer itemsProcessed = orderRepository.countItemsByEmployeeIdAndDateRange(
            employee.getId(), startDateTime, endDateTime);

        BigDecimal revenueHandled = orderRepository.sumRevenueByEmployeeIdAndDateRange(
            employee.getId(), startDateTime, endDateTime);

        Double qualityScore = qualityRepository.averageScoreByEmployeeIdAndDateRange(
            employee.getId(), startDateTime, endDateTime);

        // Attendance/on-time are only meaningful for clock-in-gated roles (workers).
        // Admins/managers have optional shifts and must not be looked up or scored
        // against shift attendance.
        boolean shiftTracked = shiftGate.isClockRequired(primaryRole, businessId);

        Double attendanceRate = null;
        Double ontimeRate = null;
        long totalMinutes = 0;
        if (shiftTracked) {
            List<EmployeeShift> shifts = shiftRepository.findByEmployeeIdAndDateRange(
                employee.getId(), startDate, endDate);

            long totalWorkingDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            long daysPresent = shifts.stream()
                .filter(s -> s.getActualStart() != null)
                .count();

            attendanceRate = totalWorkingDays > 0 ?
                (daysPresent * 100.0 / totalWorkingDays) : 0;

            long onTimeDays = shifts.stream()
                .filter(s -> s.getActualStart() != null &&
                    !s.getActualStart().isAfter(s.getScheduledStart().plusMinutes(15)))
                .count();

            ontimeRate = daysPresent > 0 ?
                (onTimeDays * 100.0 / daysPresent) : 0;

            totalMinutes = shifts.stream()
                .filter(s -> s.getTotalWorkMinutes() != null)
                .mapToLong(EmployeeShift::getTotalWorkMinutes)
                .sum();
        }

        double hoursWorked = totalMinutes / 60.0;
        int processedItems = itemsProcessed != null ? itemsProcessed : 0;
        double productivity = hoursWorked > 0 ? processedItems / hoursWorked : 0;

        // Get performance from database if available
        Optional<EmployeePerformance> savedPerf = performanceRepository.findByEmployeeIdAndPeriod(
            employee.getId(), startDate, endDate);

        double targetAchievement = savedPerf.map(EmployeePerformance::getTargetAchievement)
            .orElse(0.0);

        // Daily breakdown
        List<EmployeePerfDTO.DailyMetricDTO> dailyBreakdown = generateDailyBreakdown(
            employee.getId(), startDate, endDate, shiftTracked);

        EmployeePerfDTO.MetricsDTO metrics = EmployeePerfDTO.MetricsDTO.builder()
            .ordersProcessed(ordersProcessed != null ? ordersProcessed : 0)
            .itemsProcessed(processedItems)
            .revenueHandled(revenueHandled != null ? revenueHandled : BigDecimal.ZERO)
            .qualityScore(qualityScore != null ? qualityScore : 0.0)
            .attendanceRate(attendanceRate)
            .ontimeRate(ontimeRate)
            .productivity(productivity)
            .targetAchievement(targetAchievement)
            .build();

        return EmployeePerfDTO.builder()
            .employeeId(employee.getId())
            .employeeName(employee.getFirstName() + " " + employee.getLastName())
            .role(primaryRole)
            .period(EmployeePerfDTO.PeriodDTO.builder()
                .start(startDate)
                .end(endDate)
                .build())
            .metrics(metrics)
            .dailyBreakdown(dailyBreakdown)
            .build();
    }

    private List<EmployeePerfDTO.DailyMetricDTO> generateDailyBreakdown(
            UUID employeeId, LocalDate startDate, LocalDate endDate, boolean shiftTracked) {
        
        List<EmployeePerfDTO.DailyMetricDTO> daily = new ArrayList<>();
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.atTime(23, 59, 59);
            
            Integer dailyOrders = orderRepository.countByEmployeeIdAndDateRange(
                employeeId, dayStart, dayEnd);
            
            Integer dailyItems = orderRepository.countItemsByEmployeeIdAndDateRange(
                employeeId, dayStart, dayEnd);
            
            Double dailyQuality = qualityRepository.averageScoreByEmployeeIdAndDateRange(
                employeeId, dayStart, dayEnd);
            
            // Get hours worked
            double hoursWorked = 0.0;
            if (shiftTracked) {
                List<EmployeeShift> dayShifts = shiftRepository.findByEmployeeIdAndDateRange(
                    employeeId, current, current);

                hoursWorked = dayShifts.stream()
                    .filter(s -> s.getTotalWorkMinutes() != null)
                    .mapToDouble(s -> s.getTotalWorkMinutes() / 60.0)
                    .sum();
            }
            
            daily.add(EmployeePerfDTO.DailyMetricDTO.builder()
                .date(current)
                .orders(dailyOrders != null ? dailyOrders : 0)
                .items(dailyItems != null ? dailyItems : 0)
                .quality(dailyQuality != null ? dailyQuality : 0.0)
                .hoursWorked(hoursWorked)
                .build());
            
            current = current.plusDays(1);
        }
        
        return daily;
    }
    
    private double calculatePerformanceScore(EmployeePerfDTO perf) {
        EmployeePerfDTO.MetricsDTO m = perf.getMetrics();
        double quality = m.getQualityScore() != null ? m.getQualityScore() : 0.0;
        double productivity = m.getProductivity() != null ? m.getProductivity() : 0.0;
        double attendance = m.getAttendanceRate() != null ? m.getAttendanceRate() : 0.0;
        double target = m.getTargetAchievement() != null ? m.getTargetAchievement() : 0.0;
        return (quality * 0.3) +
               (productivity * 0.3) +
               (attendance * 0.2) +
               (target * 0.2);
    }
    
    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream()
            .anyMatch(r -> r.getRole().getName().equals(roleName));
    }
    
    private String getPrimaryRole(User user) {
        return user.getRoles().stream()
            .findFirst()
            .map(r -> r.getRole().getName())
            .orElse("EMPLOYEE");
    }
}

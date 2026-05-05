package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.employee.model.EmployeeShift;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.model.OrderItem;
import com.jjenus.qliina_management.order.repository.ItemWorkerInteractionRepository;
import com.jjenus.qliina_management.order.repository.OrderItemRepository;
import com.jjenus.qliina_management.quality.model.Defect;
import com.jjenus.qliina_management.quality.repository.DefectRepository;
import com.jjenus.qliina_management.quality.repository.QualityCheckRepository;
import com.jjenus.qliina_management.reporting.dto.WorkerDashboardDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerDashboardService {

    private final UserRepository userRepository;
    private final EmployeeShiftRepository shiftRepository;
    private final OrderItemRepository orderItemRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final DefectRepository defectRepository;
    private final ItemWorkerInteractionRepository interactionRepository;

    private static final Map<String, RoleQueueConfig> ROLE_QUEUE = Map.of(
        "WASHER", new RoleQueueConfig(
            List.of(OrderItem.ItemStatus.RECEIVED),
            OrderItem.ItemStatus.WASHING,
            "Start Washing"
        ),
        "IRONER", new RoleQueueConfig(
            List.of(OrderItem.ItemStatus.WASHED),
            OrderItem.ItemStatus.IRONING,
            "Start Ironing"
        ),
        "DELIVERY", new RoleQueueConfig(
            List.of(),
            null,
            "Start Delivery"
        )
    );

    private static final Set<String> WORKER_ROLES = Set.of("WASHER", "IRONER", "DELIVERY");

    @Transactional(readOnly = true)
    public WorkerDashboardDTO getWorkerDashboard(UUID businessId, UUID workerId) {
        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> new BusinessException("Employee not found", "EMPLOYEE_NOT_FOUND"));

        if (!businessId.equals(worker.getBusinessId())) {
            throw new BusinessException("Employee does not belong to this business", "ACCESS_DENIED");
        }

        String role = getPrimaryRole(worker);
        if (!WORKER_ROLES.contains(role)) {
            throw new BusinessException(
                "Worker dashboard is only available for worker roles (Washer, Ironer, Delivery)",
                "NOT_WORKER_ROLE");
        }

        RoleQueueConfig config = ROLE_QUEUE.get(role);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1).minusNanos(1);
        LocalDateTime weekStart = todayStart.minusDays(7);

        return WorkerDashboardDTO.builder()
                .employeeId(workerId)
                .employeeName(worker.getFirstName() + " " + worker.getLastName())
                .role(role)
                .todayMetrics(buildTodayMetrics(workerId, config, todayStart, todayEnd))
                .queueSummary(buildQueueSummary(businessId, worker.getPrimaryShopId(), config))
                .qualityOverview(buildQualityOverview(workerId, todayStart, todayEnd, weekStart, todayEnd))
                .recentItems(buildRecentItems(workerId, businessId))
                .shiftInfo(buildShiftInfo(workerId))
                .build();
    }

    private WorkerDashboardDTO.TodayMetricsDTO buildTodayMetrics(
            UUID workerId, RoleQueueConfig config, LocalDateTime start, LocalDateTime end) {

        // countItemsProcessedByEmployee returns Long (nullable wrapper)
        Long itemsProcessed = qualityCheckRepository.countItemsProcessedByEmployee(workerId, start, end);
        if (itemsProcessed == null) itemsProcessed = 0L;

        // If no QC data, fall back to status history count
        if (itemsProcessed == 0 && config.activeStatus != null) {
            Long count = orderItemRepository.countByStatusAndWorkerRoleAndDateRange(
                    config.activeStatus, workerId, start, end);
            itemsProcessed = count != null ? count : 0L;
        }

        Long itemsPassed = qualityCheckRepository.countItemsPassedByEmployee(workerId, start, end);
        if (itemsPassed == null) itemsPassed = 0L;

        Long itemsFailed = qualityCheckRepository.countItemsFailedByEmployee(workerId, start, end);
        if (itemsFailed == null) itemsFailed = 0L;

        return WorkerDashboardDTO.TodayMetricsDTO.builder()
                .itemsProcessed(itemsProcessed.intValue())
                .itemsPassedQC(itemsPassed.intValue())
                .itemsFailedQC(itemsFailed.intValue())
                .build();
    }

    private WorkerDashboardDTO.QueueSummaryDTO buildQueueSummary(
            UUID businessId, UUID shopId, RoleQueueConfig config) {

        int pendingItems = 0;
        int inProgressItems = 0;

        if (shopId != null && config.waitingStatuses != null) {
            for (OrderItem.ItemStatus status : config.waitingStatuses) {
                long count = orderItemRepository.countByBusinessIdAndShopIdAndStatus(
                        businessId, shopId, status);
                pendingItems += (int) count;
            }
        }

        if (shopId != null && config.activeStatus != null) {
            long count = orderItemRepository.countByBusinessIdAndShopIdAndStatus(
                    businessId, shopId, config.activeStatus);
            inProgressItems = (int) count;
        }

        return WorkerDashboardDTO.QueueSummaryDTO.builder()
                .pendingItems(pendingItems)
                .inProgressItems(inProgressItems)
                .nextStatusLabel(config.actionLabel)
                .build();
    }

    private WorkerDashboardDTO.QualityOverviewDTO buildQualityOverview(
            UUID workerId, LocalDateTime todayStart, LocalDateTime todayEnd,
            LocalDateTime weekStart, LocalDateTime weekEnd) {

        Double todayScore = qualityCheckRepository.averageScoreByEmployeeIdAndDateRange(
                workerId, todayStart, todayEnd);
        Double weeklyScore = qualityCheckRepository.averageScoreByEmployeeIdAndDateRange(
                workerId, weekStart, weekEnd);

        List<String> recentDefectTypes = defectRepository
                .findByReportedByAndReportedAtBetween(workerId, weekStart, weekEnd)
                .stream()
                .map(Defect::getType)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        return WorkerDashboardDTO.QualityOverviewDTO.builder()
                .todayQualityScore(todayScore != null ? todayScore : 100.0)
                .weeklyQualityScore(weeklyScore != null ? weeklyScore : 100.0)
                .recentDefectTypes(recentDefectTypes)
                .build();
    }

    private List<WorkerDashboardDTO.RecentItemDTO> buildRecentItems(
            UUID workerId, UUID businessId) {

        var pageable = PageRequest.of(0, 20);
        var interactions = interactionRepository.findByWorkerIdAndBusinessId(workerId, businessId, pageable);

        return interactions.getContent().stream()
                .map(iwi -> {
                    OrderItem item = orderItemRepository.findById(iwi.getItemId()).orElse(null);
                    if (item == null) return null;

                    return WorkerDashboardDTO.RecentItemDTO.builder()
                            .itemId(item.getId())
                            .itemNumber(item.getItemNumber())
                            .orderNumber(item.getOrder().getOrderNumber())
                            .serviceType(item.getServiceType())
                            .currentStatus(item.getStatus().toString())
                            .lastInteraction(iwi.getLastInteraction())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private WorkerDashboardDTO.ShiftInfoDTO buildShiftInfo(UUID workerId) {
        Optional<EmployeeShift> activeShift = shiftRepository.findActiveShift(workerId);

        if (activeShift.isEmpty()) {
            return WorkerDashboardDTO.ShiftInfoDTO.builder()
                    .isClockedIn(false)
                    .build();
        }

        EmployeeShift shift = activeShift.get();
        LocalDateTime now = LocalDateTime.now();
        long minutesElapsed = Duration.between(shift.getActualStart(), now).toMinutes();

        return WorkerDashboardDTO.ShiftInfoDTO.builder()
                .isClockedIn(true)
                .shiftStart(shift.getActualStart())
                .minutesElapsed(minutesElapsed)
               // .scheduledMinutes(shift.getTotalWorkMinutes())
                .breakMinutes(shift.getTotalBreakMinutes() != null ? shift.getTotalBreakMinutes() : 0)
                .build();
    }

    private String getPrimaryRole(User user) {
        return user.getRoles().stream()
                .findFirst()
                .map(ur -> ur.getRole().getName())
                .orElseThrow(() -> new BusinessException("No role assigned", "NO_ROLE"));
    }

    private record RoleQueueConfig(
            List<OrderItem.ItemStatus> waitingStatuses,
            OrderItem.ItemStatus activeStatus,
            String actionLabel
    ) {}
}
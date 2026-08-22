package com.jjenus.qliina_management.reporting.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.common.TimezoneContext;
import com.jjenus.qliina_management.employee.model.EmployeeShift;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.order.model.OrderItem;
import com.jjenus.qliina_management.order.repository.ItemStatusHistoryRepository;
import com.jjenus.qliina_management.order.repository.ItemWorkerInteractionRepository;
import com.jjenus.qliina_management.order.repository.OrderItemRepository;
import com.jjenus.qliina_management.quality.model.Defect;
import com.jjenus.qliina_management.quality.repository.DefectRepository;
import com.jjenus.qliina_management.quality.repository.QualityCheckRepository;
import com.jjenus.qliina_management.reporting.dto.WorkerDashboardDTO;
import com.jjenus.qliina_management.reporting.dto.WorkerHistoryDTO;
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
    private final ItemStatusHistoryRepository itemStatusHistoryRepository;
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
                .periodStats(buildPeriodStats(workerId, config, weekStart, todayEnd))
                .efficiency(buildEfficiency(workerId))
                .queueSummary(buildQueueSummary(businessId, worker.getPrimaryShopId(), role, config))
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

    /** Rolling window used for efficiency metrics (start → complete pairing). */
    private static final int EFFICIENCY_WINDOW_DAYS = 30;

    private WorkerDashboardDTO.PeriodStatsDTO buildPeriodStats(
            UUID workerId, RoleQueueConfig config, LocalDateTime start, LocalDateTime end) {

        int processed = itemStatusHistoryRepository.findWorkCompletions(workerId, start, end).size();

        Long passed = qualityCheckRepository.countItemsPassedByEmployee(workerId, start, end);
        Long failed = qualityCheckRepository.countItemsFailedByEmployee(workerId, start, end);

        long days = Math.max(1, Duration.between(start, end).toDays());
        return WorkerDashboardDTO.PeriodStatsDTO.builder()
                .days((int) days)
                .itemsProcessed(processed)
                .itemsPassedQC(passed != null ? passed.intValue() : 0)
                .itemsFailedQC(failed != null ? failed.intValue() : 0)
                .avgItemsPerDay(round1(processed / (double) days))
                .build();
    }

    private WorkerDashboardDTO.EfficiencyDTO buildEfficiency(UUID workerId) {
        LocalDateTime end = TimezoneContext.now();
        LocalDateTime start = end.minusDays(EFFICIENCY_WINDOW_DAYS);

        Map<UUID, List<LocalDateTime>> startsByItem = groupEvents(
                itemStatusHistoryRepository.findWorkStarts(workerId, start, end));

        List<Long> durations = new ArrayList<>();
        for (var completion : itemStatusHistoryRepository.findWorkCompletions(workerId, start, end)) {
            startsByItem.getOrDefault(completion.getItemId(), List.of()).stream()
                    .filter(s -> !s.isAfter(completion.getTimestamp()))
                    .max(LocalDateTime::compareTo)
                    .ifPresent(s -> durations.add(
                            Math.max(1, Duration.between(s, completion.getTimestamp()).toMinutes())));
        }
        Double avgMinutes = durations.isEmpty() ? null
                : round1(durations.stream().mapToLong(Long::longValue).average().orElse(0));

        int itemsThisShift = 0;
        Double itemsPerHour = null;
        var activeShift = shiftRepository.findActiveShiftUnlocked(workerId);
        if (activeShift.isPresent()) {
            LocalDateTime shiftStart = activeShift.get().getActualStart();
            itemsThisShift = itemStatusHistoryRepository
                    .findWorkCompletions(workerId, shiftStart, end).size();
            long minutesOnShift = Math.max(0, Duration.between(shiftStart, end).toMinutes());
            if (minutesOnShift >= 15) {
                itemsPerHour = round1(itemsThisShift / (minutesOnShift / 60.0));
            }
        }

        return WorkerDashboardDTO.EfficiencyDTO.builder()
                .avgMinutesPerItem(avgMinutes)
                .itemsThisShift(itemsThisShift)
                .itemsPerHourShift(itemsPerHour)
                .build();
    }

    private Map<UUID, List<LocalDateTime>> groupEvents(List<ItemStatusHistoryRepository.WorkerEventProjection> events) {
        Map<UUID, List<LocalDateTime>> byItem = new HashMap<>();
        for (var e : events) {
            byItem.computeIfAbsent(e.getItemId(), k -> new ArrayList<>()).add(e.getTimestamp());
        }
        return byItem;
    }

    /**
     * Historic daily work stats for the authenticated worker.
     * Backs GET /api/v1/{businessId}/reports/worker-dashboard/history
     */
    @Transactional(readOnly = true)
    public WorkerHistoryDTO getWorkerHistory(UUID businessId, UUID workerId, int days) {
        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> new BusinessException("Employee not found", "EMPLOYEE_NOT_FOUND"));
        if (!businessId.equals(worker.getBusinessId())) {
            throw new BusinessException("Employee does not belong to this business", "ACCESS_DENIED");
        }
        String role = getPrimaryRole(worker);
        if (!WORKER_ROLES.contains(role)) {
            throw new BusinessException(
                    "Worker history is only available for worker roles (Washer, Ironer, Delivery)",
                    "NOT_WORKER_ROLE");
        }

        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = TimezoneContext.now();

        Map<LocalDate, Integer> completionsByDay = new HashMap<>();
        Map<UUID, List<LocalDateTime>> startsByItem = new HashMap<>();
        for (var e : itemStatusHistoryRepository.findWorkStarts(workerId, start, end)) {
            startsByItem.computeIfAbsent(e.getItemId(), k -> new ArrayList<>()).add(e.getTimestamp());
        }
        Map<LocalDate, List<Long>> durationsByDay = new HashMap<>();
        for (var c : itemStatusHistoryRepository.findWorkCompletions(workerId, start, end)) {
            completionsByDay.merge(c.getTimestamp().toLocalDate(), 1, Integer::sum);
            startsByItem.getOrDefault(c.getItemId(), List.of()).stream()
                    .filter(s -> !s.isAfter(c.getTimestamp()))
                    .max(LocalDateTime::compareTo)
                    .ifPresent(s -> durationsByDay
                            .computeIfAbsent(c.getTimestamp().toLocalDate(), k -> new ArrayList<>())
                            .add(Math.max(1, Duration.between(s, c.getTimestamp()).toMinutes())));
        }

        List<WorkerHistoryDTO.DailyStatDTO> daily = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            LocalDate date = startDate.plusDays(i);
            int count = completionsByDay.getOrDefault(date, 0);
            List<Long> durs = durationsByDay.getOrDefault(date, List.of());
            daily.add(WorkerHistoryDTO.DailyStatDTO.builder()
                    .date(date)
                    .itemsProcessed(count)
                    .avgMinutesPerItem(durs.isEmpty() ? null
                            : round1(durs.stream().mapToLong(Long::longValue).average().orElse(0)))
                    .build());
        }

        return WorkerHistoryDTO.builder().days(safeDays).dailyStats(daily).build();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private WorkerDashboardDTO.QueueSummaryDTO buildQueueSummary(
            UUID businessId, UUID shopId, String role, RoleQueueConfig config) {

        int pendingItems = 0;
        int inProgressItems = 0;

        if (shopId != null && config.waitingStatuses != null) {
            for (OrderItem.ItemStatus status : config.waitingStatuses) {
                long count = countWaiting(businessId, shopId, status, "WASHER".equals(role));
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

    /**
     * Counts items waiting on a role at a given status, honoring the
     * per-item washing pipeline:
     *  - RECEIVED counts toward WASHERS only when the item needs washing;
     *  - RECEIVED counts toward IRONERS only for iron-only (express) items.
     */
    private long countWaiting(UUID businessId, UUID shopId, OrderItem.ItemStatus status, boolean washerQueueMode) {
        if (status != OrderItem.ItemStatus.RECEIVED) {
            return orderItemRepository.countByBusinessIdAndShopIdAndStatus(businessId, shopId, status);
        }
        Long washable = orderItemRepository.countReceivedRequiringWashing(businessId, shopId);
        if (washerQueueMode) {
            return washable != null ? washable : 0L;
        }
        Long total = orderItemRepository.countByBusinessIdAndShopIdAndStatus(
                businessId, shopId, status);
        long all = total != null ? total : 0L;
        long washReq = washable != null ? washable : 0L;
        return Math.max(0L, all - washReq);
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
        Optional<EmployeeShift> activeShift = shiftRepository.findActiveShiftUnlocked(workerId);

        if (activeShift.isEmpty()) {
            return WorkerDashboardDTO.ShiftInfoDTO.builder()
                    .isClockedIn(false)
                    .build();
        }

        EmployeeShift shift = activeShift.get();
        LocalDateTime now = TimezoneContext.now();
        long minutesElapsed = Math.max(0, Duration.between(shift.getActualStart(), now).toMinutes());

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
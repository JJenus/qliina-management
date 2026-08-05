package com.jjenus.qliina_management.employee.service;

import com.jjenus.qliina_management.common.TimezoneContext;
import com.jjenus.qliina_management.common.websocket.WebSocketPublisher;
import com.jjenus.qliina_management.employee.model.EmployeeShift;
import com.jjenus.qliina_management.employee.model.TimeEntry;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.employee.repository.TimeEntryRepository;
import com.jjenus.qliina_management.identity.service.BusinessConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdleDetectionService {

    private final EmployeeShiftRepository shiftRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final BusinessConfigService configService;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void autoSuspendIdleShifts() {
        List<EmployeeShift> candidates = shiftRepository.findCandidatesForIdleCheck();

        for (EmployeeShift shift : candidates) {
            try {
                ZoneId zone = resolveZone(shift.getBusinessId());
                TimezoneContext.set(zone);

                int timeoutMinutes = 120;
                if (shift.getBusinessId() != null) {
                    var config = configService.getConfig(shift.getBusinessId());
                    if (config.getIdleTimeoutMinutes() != null && config.getIdleTimeoutMinutes() > 0) {
                        timeoutMinutes = config.getIdleTimeoutMinutes();
                    }
                }

                if (shift.getLastActivityAt() == null
                    || shift.getLastActivityAt().isAfter(TimezoneContext.now().minusMinutes(timeoutMinutes))) {
                    TimezoneContext.clear();
                    continue;
                }

                if (shift.getStatus() == EmployeeShift.ShiftStatus.ON_BREAK) {
                    shift.endBreak();
                }

                shift.suspend();
                shiftRepository.save(shift);

                TimeEntry entry = new TimeEntry();
                entry.setBusinessId(shift.getBusinessId());
                entry.setEmployeeId(shift.getEmployeeId());
                entry.setShopId(shift.getShopId());
                entry.setEventType(TimeEntry.EventType.SUSPEND);
                entry.setTimestamp(TimezoneContext.now());
                entry.setShiftId(shift.getId());
                entry.setNotes("Auto-suspended due to inactivity");
                timeEntryRepository.save(entry);

                Map<String, Object> payload = Map.of(
                    "eventType", "SHIFT_AUTO_SUSPENDED",
                    "shiftId", shift.getId().toString(),
                    "reason", "inactivity"
                );
                webSocketPublisher.publishUserNotification(shift.getEmployeeId(), payload);

                log.info("Auto-suspended shift {} for employee {} (idle > {} min)",
                    shift.getId(), shift.getEmployeeId(), timeoutMinutes);
            } catch (Exception e) {
                log.error("Failed to auto-suspend shift {}: {}", shift.getId(), e.getMessage());
            } finally {
                TimezoneContext.clear();
            }
        }
    }

    private ZoneId resolveZone(UUID businessId) {
        if (businessId == null) return ZoneId.of("Africa/Lagos");
        try {
            var config = configService.getConfig(businessId);
            String tz = config.getTimezone();
            return tz != null ? ZoneId.of(tz) : ZoneId.of("Africa/Lagos");
        } catch (Exception e) {
            return ZoneId.of("Africa/Lagos");
        }
    }
}

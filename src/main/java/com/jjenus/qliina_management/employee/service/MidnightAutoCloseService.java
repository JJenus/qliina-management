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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MidnightAutoCloseService {

    private final EmployeeShiftRepository shiftRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final BusinessConfigService configService;

    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void autoCloseActiveShifts() {
        List<EmployeeShift> openShifts = shiftRepository.findAllActiveShifts();
        if (openShifts == null || openShifts.isEmpty()) {
            log.debug("Midnight auto-close: no open shifts found");
            return;
        }

        for (EmployeeShift shift : openShifts) {
            try {
                ZoneId zone = resolveZone(shift.getBusinessId());
                TimezoneContext.set(zone);
                LocalTime now = TimezoneContext.now().toLocalTime();
                LocalTime cutoff = LocalTime.parse("00:00");
                if (shift.getBusinessId() != null) {
                    var config = configService.getConfig(shift.getBusinessId());
                    if (config.getDayCutoffTime() != null) {
                        try {
                            cutoff = LocalTime.parse(config.getDayCutoffTime());
                        } catch (Exception e) {
                            log.warn("Invalid dayCutoffTime '{}' for business {}, using 00:00",
                                config.getDayCutoffTime(), shift.getBusinessId());
                        }
                    }
                }

                if (now.isBefore(cutoff)) {
                    TimezoneContext.clear();
                    continue;
                }

                if (shift.getStatus() == EmployeeShift.ShiftStatus.ON_BREAK) {
                    shift.endBreak();
                }

                LocalDateTime closeTime = TimezoneContext.now();
                shift.setActualEnd(closeTime);
                shift.setStatus(EmployeeShift.ShiftStatus.CHECKED_OUT);
                shift.setAutoClosed(true);
                shift.calculateWorkMinutes();
                shiftRepository.save(shift);

                TimeEntry entry = new TimeEntry();
                entry.setBusinessId(shift.getBusinessId());
                entry.setEmployeeId(shift.getEmployeeId());
                entry.setShopId(shift.getShopId());
                entry.setEventType(TimeEntry.EventType.AUTO_CLOSED);
                entry.setTimestamp(closeTime);
                entry.setShiftId(shift.getId());
                entry.setNotes("Auto-closed at cutoff " + cutoff);
                timeEntryRepository.save(entry);

                Map<String, Object> payload = Map.of(
                    "eventType", "SHIFT_AUTO_CLOSED",
                    "shiftId", shift.getId().toString(),
                    "autoClosed", true
                );
                webSocketPublisher.publishUserNotification(shift.getEmployeeId(), payload);

                log.info("Auto-closed shift {} for employee {}", shift.getId(), shift.getEmployeeId());
            } catch (Exception e) {
                log.error("Failed to auto-close shift {}: {}", shift.getId(), e.getMessage());
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

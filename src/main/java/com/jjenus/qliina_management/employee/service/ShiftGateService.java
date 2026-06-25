package com.jjenus.qliina_management.employee.service;

import com.jjenus.qliina_management.common.BusinessException;
import com.jjenus.qliina_management.employee.repository.EmployeeShiftRepository;
import com.jjenus.qliina_management.identity.repository.UserRepository;
import com.jjenus.qliina_management.identity.service.BusinessConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftGateService {

    private static final Set<String> DEFAULT_CLOCK_REQUIRED_ROLES = Set.of("WASHER", "IRONER", "DELIVERY", "FRONT_DESK");

    private final EmployeeShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final BusinessConfigService configService;

    public boolean isClockRequired(String role, UUID businessId) {
        if (DEFAULT_CLOCK_REQUIRED_ROLES.contains(role)) return true;
        if (businessId == null) return false;
        try {
            var config = configService.getConfig(businessId);
            if (config.getRequireClockInRoles() != null && !config.getRequireClockInRoles().isEmpty()) {
                for (String r : config.getRequireClockInRoles().split(",")) {
                    if (r.trim().equalsIgnoreCase(role)) return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read requireClockInRoles for business {}", businessId);
        }
        return false;
    }

    @Transactional
    public void requireClockedIn(UUID workerId, String role, UUID businessId) {
        if (isClockRequired(role, businessId)) {
            shiftRepository.findActiveShift(workerId)
                .orElseThrow(() -> new BusinessException(
                    "You must clock in before you can start working.",
                    "NOT_CLOCKED_IN"));
        }
    }

    public String getPrimaryRole(UUID userId) {
        return userRepository.findById(userId)
            .flatMap(user -> user.getRoles().stream().findFirst())
            .map(ur -> ur.getRole().getName())
            .orElseThrow(() -> new BusinessException("No role assigned", "NO_ROLE"));
    }
}

package com.jjenus.qliina_management.common;

import com.jjenus.qliina_management.common.util.SecurityContextUtil;
import com.jjenus.qliina_management.employee.service.ShiftGateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ClockInGateAspect {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final ShiftGateService shiftGateService;

    @Pointcut("@within(com.jjenus.qliina_management.common.RequireClockIn)")
    public void clockInRequiredClass() {}

    @Pointcut("execution(public * *(..))")
    public void publicMethod() {}

    @Around("clockInRequiredClass() && publicMethod()")
    public Object enforceClockIn(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        if (READ_METHODS.contains(request.getMethod())) {
            return pjp.proceed();
        }

        UUID workerId = SecurityContextUtil.requireUserId();
        String role = shiftGateService.getPrimaryRole(workerId);
        UUID businessId = extractBusinessId(request);

        shiftGateService.requireClockedIn(workerId, role, businessId);

        return pjp.proceed();
    }

    private UUID extractBusinessId(HttpServletRequest request) {
        String path = request.getRequestURI();
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if ("api".equals(segments[i]) && "v1".equals(segments[i + 1]) && i + 2 < segments.length) {
                try {
                    return UUID.fromString(segments[i + 2]);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }
}

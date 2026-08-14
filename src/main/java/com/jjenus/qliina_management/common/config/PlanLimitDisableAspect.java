package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.business.service.PlanLimitService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global kill-switch for subscription plan-limit enforcement.
 *
 * <p>When {@code app.subscription.enforce-limits} is {@code false} every
 * {@link PlanLimitService#enforceShopLimit enforce*} call is skipped, so hard
 * limits (PLAN_LIMIT_EXCEEDED) and feature gates (FEATURE_NOT_AVAILABLE) are
 * not applied anywhere on the platform. Defaults to {@code true}; the property
 * is read at call time so it can be toggled per environment without a restart.
 */
@Slf4j
@Aspect
@Component
public class PlanLimitDisableAspect {

    @Value("${app.subscription.enforce-limits:true}")
    private boolean enforceLimits;

    @Around("execution(* com.jjenus.qliina_management.business.service.PlanLimitService.enforce*(..))")
    public Object skipPlanEnforcement(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!enforceLimits) {
            log.debug("Plan-limit enforcement disabled — skipping {}", joinPoint.getSignature().getName());
            return null;
        }
        return joinPoint.proceed();
    }
}

package com.jjenus.qliina_management.common;

import com.jjenus.qliina_management.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    
    private final AuditService auditService;
    
    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void postMapping() {}
    
    @Pointcut("@annotation(org.springframework.web.bind.annotation.PutMapping)")
    public void putMapping() {}
    
    @Pointcut("@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void deleteMapping() {}
    
    @Pointcut("@annotation(org.springframework.web.bind.annotation.PatchMapping)")
    public void patchMapping() {}
    
    @Pointcut("@annotation(com.jjenus.qliina_management.common.Auditable)")
    public void auditableMethods() {}
    
    @Around("postMapping() || putMapping() || deleteMapping() || patchMapping() || auditableMethods()")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        Object result = null;
        Throwable error = null;
        
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            
            try {
                String entityType = extractEntityType(className);
                UUID entityId = extractEntityId(joinPoint.getArgs());
                String action = buildActionName(className, methodName);
                
                Object oldValue = extractOldValue(joinPoint);
                Object newValue = result != null ? result : joinPoint.getArgs().length > 0 ? joinPoint.getArgs()[0] : null;
                String details = buildDetails(joinPoint, executionTime, error);
                
                auditService.logEvent(
                    entityType,
                    entityId,
                    action,
                    oldValue,
                    newValue,
                    details
                );
                
            } catch (Exception e) {
                log.error("Failed to create audit log", e);
            }
        }
    }
    
    @AfterReturning(pointcut = "@annotation(com.jjenus.qliina_management.common.Auditable)", returning = "result")
    public void auditCustom(JoinPoint joinPoint, Object result) {
        // Custom audit logic for specific methods
    }
    
    private String extractEntityType(String className) {
        return className.replace("Controller", "")
            .replace("Service", "")
            .toUpperCase();
    }
    
    private UUID extractEntityId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID) {
                return (UUID) arg;
            }
            if (arg instanceof String && ((String) arg).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                return UUID.fromString((String) arg);
            }
        }
        return null;
    }
    
    private String buildActionName(String className, String methodName) {
        if (methodName.startsWith("create")) return "CREATE";
        if (methodName.startsWith("update")) return "UPDATE";
        if (methodName.startsWith("delete")) return "DELETE";
        if (methodName.startsWith("cancel")) return "CANCEL";
        if (methodName.startsWith("transfer")) return "TRANSFER";
        return methodName.toUpperCase();
    }
    
    private Object extractOldValue(ProceedingJoinPoint joinPoint) {
        // In a real implementation, you might fetch the old value from database
        // before the update for comparison
        return null;
    }
    
    private String buildDetails(ProceedingJoinPoint joinPoint, long executionTime, Throwable error) {
        StringBuilder details = new StringBuilder();
        
        details.append("Method: ").append(joinPoint.getSignature().getName()).append("\n");
        details.append("Args: ").append(Arrays.toString(joinPoint.getArgs())).append("\n");
        details.append("Execution time: ").append(executionTime).append("ms\n");
        
        if (error != null) {
            details.append("Error: ").append(error.getMessage()).append("\n");
        }
        
        return details.toString();
    }
}

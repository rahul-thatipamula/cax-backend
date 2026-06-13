package com.cax.cax_backend.common.aspect;

import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.logging.AdminActivityFileLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * Aspect to intercept methods annotated with @AdminActivityLog
 * and record them in the activity log file.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminActivityAspect {

    private final AdminActivityFileLogger fileLogger;

    @Around("@annotation(adminActivityLog)")
    public Object logActivity(ProceedingJoinPoint joinPoint, AdminActivityLog adminActivityLog) throws Throwable {
        String adminId = "SYSTEM";
        
        // 1. Get currently authenticated admin User ID from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            adminId = auth.getName();
        }

        // 2. Extract resource ID if configured in the annotation
        String resourceId = null;
        String resourceParamName = adminActivityLog.resourceIdParam();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();

        if (resourceParamName != null && !resourceParamName.isBlank() && parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                if (resourceParamName.equals(parameterNames[i]) && args[i] != null) {
                    resourceId = args[i].toString();
                    break;
                }
            }
        }

        // Fallback: if no resourceIdParam specified but we have a "userId", "id", or "postId" parameter
        if (resourceId == null && parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                String name = parameterNames[i].toLowerCase();
                if (args[i] != null && (name.equals("id") || name.equals("userid") || name.equals("postid") || name.equals("adid") || name.equals("collegeid"))) {
                    resourceId = args[i].toString();
                    break;
                }
            }
        }

        // 3. Extract details from request body (usually the first Map or DTO argument)
        String details = "";
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof Map) {
                    details = arg.toString();
                    break;
                } else if (arg != null && !arg.getClass().getName().startsWith("java.lang") && !arg.getClass().getName().startsWith("org.springframework")) {
                    details = arg.toString(); // For DTOs
                    break;
                }
            }
        }

        Object result;
        try {
            result = joinPoint.proceed();
            // Log successful action
            fileLogger.log(adminId, adminActivityLog.action(), resourceId, details, "SUCCESS");
            return result;
        } catch (Throwable e) {
            // Log failed action with exception message
            String errorDetails = details + " (Error: " + e.getMessage() + ")";
            fileLogger.log(adminId, adminActivityLog.action(), resourceId, errorDetails, "FAILED");
            throw e;
        }
    }
}

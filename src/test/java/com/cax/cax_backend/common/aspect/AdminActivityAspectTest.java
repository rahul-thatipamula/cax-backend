package com.cax.cax_backend.common.aspect;

import com.cax.cax_backend.common.annotation.AdminActivityLog;
import com.cax.cax_backend.common.logging.AdminActivityFileLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminActivityAspectTest {

    private AdminActivityFileLogger fileLogger;
    private AdminActivityAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;
    private SecurityContext securityContext;
    private Authentication authentication;
    private AdminActivityLog annotation;

    @BeforeEach
    void setUp() {
        fileLogger = mock(AdminActivityFileLogger.class);
        aspect = new AdminActivityAspect(fileLogger);
        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        annotation = mock(AdminActivityLog.class);

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("admin_user_456");

        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testLogActivitySuccessWithConfiguredParam() throws Throwable {
        when(annotation.action()).thenReturn("Block User Action");
        when(annotation.resourceIdParam()).thenReturn("userId");

        when(signature.getParameterNames()).thenReturn(new String[]{"userId", "blocked"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"target_user_88", true});
        when(joinPoint.proceed()).thenReturn("Method Result");

        Object result = aspect.logActivity(joinPoint, annotation);

        assertEquals("Method Result", result);
        verify(fileLogger).log(
                eq("admin_user_456"),
                eq("Block User Action"),
                eq("target_user_88"),
                anyString(),
                eq("SUCCESS")
        );
    }

    @Test
    void testLogActivityFallbackParam() throws Throwable {
        when(annotation.action()).thenReturn("Delete Item Action");
        when(annotation.resourceIdParam()).thenReturn(""); // Empty string trigger fallback

        // fallback parameter named "id"
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"item_abc_999"});
        when(joinPoint.proceed()).thenReturn("Deleted");

        aspect.logActivity(joinPoint, annotation);

        verify(fileLogger).log(
                eq("admin_user_456"),
                eq("Delete Item Action"),
                eq("item_abc_999"),
                anyString(),
                eq("SUCCESS")
        );
    }

    @Test
    void testLogActivityFailure() throws Throwable {
        when(annotation.action()).thenReturn("Ad Create Fail");
        when(annotation.resourceIdParam()).thenReturn("");

        when(signature.getParameterNames()).thenReturn(new String[]{});
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        
        RuntimeException exception = new RuntimeException("Validation error");
        when(joinPoint.proceed()).thenThrow(exception);

        assertThrows(RuntimeException.class, () -> aspect.logActivity(joinPoint, annotation));

        verify(fileLogger).log(
                eq("admin_user_456"),
                eq("Ad Create Fail"),
                isNull(),
                contains("Validation error"),
                eq("FAILED")
        );
    }
}

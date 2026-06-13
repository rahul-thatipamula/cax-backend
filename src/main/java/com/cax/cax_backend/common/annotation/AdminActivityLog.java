package com.cax.cax_backend.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to mark admin actions for audit logging.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminActivityLog {
    /**
     * The name of the action being logged.
     */
    String action();

    /**
     * Optional parameter name in the method arguments to treat as the resource ID.
     */
    String resourceIdParam() default "";
}

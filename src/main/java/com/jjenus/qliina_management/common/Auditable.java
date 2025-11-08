package com.jjenus.qliina_management.common;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    String value() default "";
    boolean includeArgs() default true;
    boolean includeResult() default true;
}

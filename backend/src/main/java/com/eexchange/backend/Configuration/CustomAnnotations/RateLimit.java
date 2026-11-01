package com.eexchange.backend.Configuration.CustomAnnotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    enum Scope {
        USER,
        IP
    }

    int limit() default 10;

    int windowSeconds() default 60;

    String name() default "";

    Scope scope() default Scope.USER;
}
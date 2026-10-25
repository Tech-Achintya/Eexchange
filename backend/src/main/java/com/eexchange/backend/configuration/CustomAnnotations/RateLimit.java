package com.eexchange.backend.configuration.CustomAnnotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
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
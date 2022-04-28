package com.oracle.truffle.api.operation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GenerateOperations {
    String decisionsFile() default "";

    String[] decisionOverrideFiles() default {};

    Class<?>[] boxingEliminationTypes() default {};
}

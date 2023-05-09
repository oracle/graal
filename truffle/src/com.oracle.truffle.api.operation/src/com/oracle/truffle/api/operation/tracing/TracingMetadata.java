package com.oracle.truffle.api.operation.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface TracingMetadata {
    String decisionsFile();

    String[] instructionNames();

    SpecializationNames[] specializationNames();

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface SpecializationNames {
        String instruction();

        String[] specializations();
    }
}